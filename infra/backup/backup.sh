#!/usr/bin/env bash
# 가까워 운영 DB 일일 백업 스크립트
# DB 덤프, 프로필 이미지, manifest를 tar.gz 하나로 묶어 Cloudflare R2에 올린다.
# 설치/운영/복구 절차는 같은 폴더의 README.md 참고. cron이 매일 04:00 KST에 실행한다.

set -Eeuo pipefail
umask 077
# cron 기본 PATH에는 /usr/local/bin이 없어 rclone을 못 찾는다
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# ----- 설정 (환경변수로 덮어쓸 수 있음, README의 표 참고) -----
SCRIPT_NAME="backup"
SCRIPT_TITLE="일일 백업"
GAKKAWEO_HOME="${GAKKAWEO_HOME:-$HOME/gakkaweo}"
BACKUP_DIR="${BACKUP_DIR:-$GAKKAWEO_HOME/backups}"
ENV_FILE="${ENV_FILE:-$GAKKAWEO_HOME/.env.prod}"
NOTIFY_LOG="$BACKUP_DIR/.notify-backup.$$.log" # 실패 알림에 싣는 자체 진행 로그 (매 실행 새로 생성)
LOCK_FILE="$BACKUP_DIR/.backup.lock"
RCLONE_CONFIG_FILE="${RCLONE_CONFIG_FILE:-$HOME/.config/rclone/rclone.conf}"
RETENTION_MTIME="${BACKUP_RETENTION_MTIME:-6}"            # find -mtime +6 = 로컬 7일 보관
MIN_DUMP_BYTES="${BACKUP_MIN_DUMP_BYTES:-102400}"         # 덤프 크기 하한 100KB
MIN_FREE_KB="${BACKUP_MIN_FREE_KB:-1048576}"              # 디스크 여유 공간 하한 1GB
QUOTA_WARN_BYTES="${BACKUP_QUOTA_WARN_BYTES:-8000000000}" # R2 사용량 경고 기준 8GB (무료 10GB의 80%)
SKIP_UPLOAD="${BACKUP_SKIP_UPLOAD:-0}"                    # 1이면 R2 업로드 생략 (최초 설치 확인용)

# 공통 함수 로드 (같은 폴더의 lib.sh - 로그, 알림, 잠금, .env 파서)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

# ----- .env.prod에서 읽는 설정 -----
DISCORD_WEBHOOK_URL="$(read_env DISCORD_WEBHOOK_URL)"
DISCORD_MENTION_ROLE_ID="$(read_env DISCORD_MENTION_ROLE_ID)"
HC_URL="$(read_env BACKUP_HEALTHCHECK_URL)"
R2_REMOTE="$(read_env BACKUP_R2_REMOTE r2)"
R2_BUCKET="$(read_env BACKUP_R2_BUCKET gakkaweo-backup)"

WORK=""
PARTIAL=""
WARNINGS=()

compose() {
    (cd "$GAKKAWEO_HOME" && docker compose -f docker-compose.prod.yml --env-file .env.prod "$@")
}

cleanup() {
    local code=$?
    if [ -n "$WORK" ]; then rm -rf "$WORK" 2>/dev/null || true; fi
    if [ -n "$PARTIAL" ]; then rm -f "$PARTIAL" 2>/dev/null || true; fi
    rm -f "$NOTIFY_LOG" 2>/dev/null || true
    exit "$code"
}
trap cleanup EXIT
trap 'on_error $LINENO $?' ERR

main() {
    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"

    # 중복 실행이면 조용히 종료 (알림 없음)
    if ! acquire_lock -n; then
        log "다른 백업/리허설이 실행 중 - 종료"
        exit 0
    fi

    # 강제 종료나 정전으로 남은 옛 작업 파일 정리 (잠금을 쥔 상태라 안전)
    find "$BACKUP_DIR" -maxdepth 1 \( -name '.work.*' -o -name '.restore.*' \) -mmin +720 -exec rm -rf {} + 2>/dev/null || true
    find "$BACKUP_DIR" -maxdepth 1 \( -name '.gakkaweo_*.partial' -o -name '.notify-*.log' \) -mmin +720 -delete 2>/dev/null || true

    STEP="사전 점검"
    require_cmd docker rclone curl jq tar sha256sum flock
    local cid free_kb
    cid="$(compose ps -q postgresql)"
    if [ -z "$cid" ] || [ "$(docker inspect -f '{{.State.Running}}' "$cid")" != "true" ]; then
        die "postgresql 컨테이너가 실행 중이 아님"
    fi
    free_kb="$(df -Pk "$BACKUP_DIR" | awk 'NR==2 {print $4}')"
    if [ "$free_kb" -lt "$MIN_FREE_KB" ]; then
        die "디스크 여유 공간 부족: ${free_kb}KB (하한 ${MIN_FREE_KB}KB)"
    fi

    STEP="healthchecks start"
    hc_ping start

    # 파일명과 주간/월간 판정은 호스트 타임존과 무관하게 KST 기준으로 계산
    local ts weekday dom name
    read -r ts weekday dom <<<"$(TZ=Asia/Seoul date '+%Y%m%d_%H%M%S %u %d')"
    name="gakkaweo_${ts}.tar.gz"

    # 작업 디렉토리는 BACKUP_DIR 아래에 만든다 (mv가 같은 파일시스템 안에서만 원자적이라서)
    STEP="작업 디렉토리 생성"
    WORK="$(mktemp -d "$BACKUP_DIR/.work.XXXXXX")"

    # -T 필수 (TTY가 붙으면 바이너리가 깨진다). 덤프 출력에 2>&1 금지
    STEP="DB 덤프"
    compose exec -T postgresql sh -c 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' >"$WORK/db.dump"
    local dump_bytes
    dump_bytes="$(stat -c %s "$WORK/db.dump")"
    if [ "$dump_bytes" -lt "$MIN_DUMP_BYTES" ]; then
        die "덤프 크기가 하한 미만: ${dump_bytes}B (하한 ${MIN_DUMP_BYTES}B)"
    fi

    STEP="덤프 스모크 검증"
    compose exec -T postgresql pg_restore -l <"$WORK/db.dump" >/dev/null ||
        die "pg_restore 목록 검증 실패 - 덤프 손상 의심"

    # 덤프 직후에 바로 세어, 그 사이 데이터가 바뀔 틈을 줄인다
    STEP="row count 수집"
    local counts rc_members rc_guess rc_daily n
    counts="$(printf 'SELECT count(*) FROM members;\nSELECT count(*) FROM guess_history;\nSELECT count(*) FROM daily_sentences;\n' |
        compose exec -T postgresql sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -f -')"
    rc_members="$(sed -n 1p <<<"$counts")"
    rc_guess="$(sed -n 2p <<<"$counts")"
    rc_daily="$(sed -n 3p <<<"$counts")"
    for n in "$rc_members" "$rc_guess" "$rc_daily"; do
        if ! [[ "$n" =~ ^[0-9]+$ ]]; then
            die "row count 수집 결과가 숫자가 아님: '$counts'"
        fi
    done

    # tar 종료코드 1(읽는 중 파일 변경)은 경고로 넘기고, 그 외 실패는 중단
    STEP="uploads 아카이브"
    local tar_rc=0
    tar -cf "$WORK/uploads.tar" -C "$GAKKAWEO_HOME" uploads || tar_rc=$?
    if [ "$tar_rc" -eq 1 ]; then
        log "WARN: uploads tar 중 파일 변경 감지 (exit 1)"
        WARNINGS+=("uploads tar 중 파일 변경 감지")
    elif [ "$tar_rc" -ne 0 ]; then
        die "uploads tar 실패 (exit $tar_rc)"
    fi

    # 이미지 개수는 tar 목록에서 센다 (manifest는 실제로 묶인 내용을 설명해야 하므로)
    STEP="이미지 개수 집계"
    local image_count
    image_count="$(tar -tf "$WORK/uploads.tar" | grep -c -v '/$' || true)"

    STEP="manifest 생성"
    local sha_dump sha_uploads created_at
    sha_dump="$(sha256sum "$WORK/db.dump" | awk '{print $1}')"
    sha_uploads="$(sha256sum "$WORK/uploads.tar" | awk '{print $1}')"
    created_at="$(TZ=Asia/Seoul date '+%Y-%m-%dT%H:%M:%S+09:00')"
    jq -n \
        --arg created_at "$created_at" --arg archive "$name" \
        --argjson members "$rc_members" --argjson guess_history "$rc_guess" --argjson daily_sentences "$rc_daily" \
        --argjson image_count "$image_count" \
        --arg sha_db "$sha_dump" --arg sha_uploads "$sha_uploads" \
        '{created_at: $created_at, archive: $archive,
          row_counts: {members: $members, guess_history: $guess_history, daily_sentences: $daily_sentences},
          image_count: $image_count,
          sha256: {"db.dump": $sha_db, "uploads.tar": $sha_uploads}}' >"$WORK/manifest.json"

    # .partial로 만들었다가 mv 한다 - 완성된 파일만 최종 이름을 갖는다
    STEP="아카이브 묶기"
    PARTIAL="$BACKUP_DIR/.$name.partial"
    tar -czf "$PARTIAL" -C "$WORK" db.dump uploads.tar manifest.json
    mv "$PARTIAL" "$BACKUP_DIR/$name"
    PARTIAL=""
    local sha_archive archive_bytes archive_human
    sha_archive="$(sha256sum "$BACKUP_DIR/$name" | awk '{print $1}')"
    archive_bytes="$(stat -c %s "$BACKUP_DIR/$name")"
    archive_human="$(awk -v b="$archive_bytes" 'BEGIN{printf "%.1f MiB", b/1048576}')"

    local usage_text="측정 안 함 (업로드 생략)"
    if [ "$SKIP_UPLOAD" = "1" ]; then
        log "BACKUP_SKIP_UPLOAD=1 - R2 업로드 생략"
        WARNINGS+=("R2 업로드 생략 (BACKUP_SKIP_UPLOAD=1)")
    else
        STEP="R2 업로드 (daily)"
        rclone --config "$RCLONE_CONFIG_FILE" copyto "$BACKUP_DIR/$name" \
            "$R2_REMOTE:$R2_BUCKET/daily/$name" --retries 3 || die "R2 daily 업로드 실패"

        # 일요일이면 weekly/, 매월 1일이면 monthly/에도 복사 (R2 안에서 복사, 실패하면 로컬에서 재업로드)
        local target
        for target in weekly monthly; do
            if { [ "$target" = "weekly" ] && [ "$weekday" = "7" ]; } ||
                { [ "$target" = "monthly" ] && [ "$dom" = "01" ]; }; then
                STEP="R2 복사 ($target)"
                if ! rclone --config "$RCLONE_CONFIG_FILE" copyto \
                    "$R2_REMOTE:$R2_BUCKET/daily/$name" "$R2_REMOTE:$R2_BUCKET/$target/$name" --retries 3; then
                    log "WARN: $target 서버사이드 복사 실패 - 로컬에서 재업로드 시도"
                    if ! rclone --config "$RCLONE_CONFIG_FILE" copyto \
                        "$BACKUP_DIR/$name" "$R2_REMOTE:$R2_BUCKET/$target/$name" --retries 3; then
                        log "WARN: $target 재업로드도 실패"
                        WARNINGS+=("R2 $target 복사 실패 (daily에는 업로드됨)")
                    fi
                fi
            fi
        done

        STEP="R2 사용량 측정"
        local usage_bytes
        usage_bytes="$(rclone --config "$RCLONE_CONFIG_FILE" size "$R2_REMOTE:$R2_BUCKET" --json 2>/dev/null | jq -r '.bytes' || true)"
        if [[ "$usage_bytes" =~ ^[0-9]+$ ]]; then
            usage_text="$(awk -v b="$usage_bytes" 'BEGIN{printf "%.2f GB / 10 GB", b/1000000000}')"
            if [ "$usage_bytes" -gt "$QUOTA_WARN_BYTES" ]; then
                log "WARN: R2 사용량 8GB 초과: $usage_text"
                notify_discord "$COLOR_WARN" "R2 사용량 경고" "버킷 사용량이 8GB를 넘었습니다. lifecycle 규칙과 백업 크기를 점검하세요.
현재 사용량: $usage_text" "$DISCORD_MENTION_ROLE_ID"
            fi
        else
            usage_text="측정 실패"
            WARNINGS+=("R2 사용량 측정 실패")
        fi
    fi

    STEP="로컬 보관 정리"
    find "$BACKUP_DIR" -maxdepth 1 -name 'gakkaweo_*.tar.gz' -mtime "+$RETENTION_MTIME" -delete

    STEP="완료 알림"
    local color="$COLOR_SUCCESS" title="백업 성공" warn_text=""
    if [ "${#WARNINGS[@]}" -gt 0 ]; then
        color="$COLOR_WARN"
        title="백업 성공 (경고 있음)"
        warn_text=$'\n\n경고:\n'"$(printf -- '- %s\n' "${WARNINGS[@]}")"
    fi
    notify_discord "$color" "$title" "파일: $name
크기: $archive_human (${archive_bytes} bytes)
SHA-256: $sha_archive
row count: members=$rc_members, guess_history=$rc_guess, daily_sentences=$rc_daily
이미지: ${image_count}개
R2 사용량: $usage_text$warn_text"

    STEP="healthchecks 성공 ping"
    hc_ping success
    log "백업 완료: $name ($archive_human)"
}

main "$@"
