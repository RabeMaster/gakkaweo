#!/usr/bin/env bash
# 가까워 운영 DB 주간 복원 리허설 스크립트
# R2의 최신 daily 백업을 내려받아 임시 PostgreSQL 컨테이너에 실제로 복원하고
# manifest와 대조해 검증한다. cron이 매주 일요일 04:30 KST에 실행한다.
# 설치/운영/복구 절차는 같은 폴더의 README.md 참고.

set -Eeuo pipefail
umask 077
# cron 기본 PATH에는 /usr/local/bin이 없어 rclone을 못 찾는다
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# ----- 설정 (환경변수로 덮어쓸 수 있음, README의 표 참고) -----
SCRIPT_NAME="restore-test"
SCRIPT_TITLE="복원 리허설"
GAKKAWEO_HOME="${GAKKAWEO_HOME:-$HOME/gakkaweo}"
BACKUP_DIR="${BACKUP_DIR:-$GAKKAWEO_HOME/backups}"
ENV_FILE="${ENV_FILE:-$GAKKAWEO_HOME/.env.prod}"
NOTIFY_LOG="$BACKUP_DIR/.notify-restore-test.$$.log" # 실패 알림에 싣는 자체 진행 로그 (매 실행 새로 생성)
LOCK_FILE="$BACKUP_DIR/.backup.lock"
RCLONE_CONFIG_FILE="${RCLONE_CONFIG_FILE:-$HOME/.config/rclone/rclone.conf}"
LOCK_WAIT_SECONDS="${RESTORE_LOCK_WAIT_SECONDS:-1800}" # 백업이 늦어지면 최대 30분 대기
PG_READY_TIMEOUT="${RESTORE_PG_READY_TIMEOUT:-60}"     # 임시 PostgreSQL 준비 대기 상한(초)
MIN_FREE_KB="${RESTORE_MIN_FREE_KB:-1000000}"          # 디스크 여유 공간 하한 약 1GB (df -Pk 블록 수)
RESTORE_CONTAINER="gakkaweo-restore-test"

# 공통 함수 로드 (같은 폴더의 lib.sh - 로그, 알림, 잠금, .env 파서)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

# ----- .env.prod에서 읽는 설정 -----
DISCORD_WEBHOOK_URL="$(read_env DISCORD_WEBHOOK_URL)"
HC_URL="$(read_env RESTORE_TEST_HEALTHCHECK_URL)"
R2_REMOTE="$(read_env BACKUP_R2_REMOTE r2)"
R2_BUCKET="$(read_env BACKUP_R2_BUCKET gakkaweo-backup)"

WORK=""

cleanup() {
    local code=$?
    # -v 필수: 없으면 임시 컨테이너의 익명 볼륨이 매주 누적된다
    docker rm -f -v "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
    if [ -n "$WORK" ]; then rm -rf "$WORK" 2>/dev/null || true; fi
    rm -f "$NOTIFY_LOG" 2>/dev/null || true
    exit "$code"
}
trap cleanup EXIT
trap 'on_error $LINENO $?' ERR

main() {
    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"

    # backup.sh와 잠금을 공유한다 - 백업이 돌고 있으면 끝날 때까지 기다린다
    STEP="잠금 획득"
    acquire_lock -w "$LOCK_WAIT_SECONDS" || die "잠금 획득 실패 (${LOCK_WAIT_SECONDS}초 초과)"

    STEP="사전 점검"
    require_cmd docker rclone curl jq tar sha256sum flock openssl
    local free_kb
    free_kb="$(df -Pk "$BACKUP_DIR" | awk 'NR==2 {print $4}')"
    if [ "$free_kb" -lt "$MIN_FREE_KB" ]; then
        die "디스크 여유 공간 부족: ${free_kb}KB (하한 ${MIN_FREE_KB}KB)"
    fi
    STEP="healthchecks start"
    hc_ping start

    local start_epoch
    start_epoch="$(date +%s)"

    STEP="최신 백업 조회"
    local latest
    latest="$(rclone --config "$RCLONE_CONFIG_FILE" lsf "$R2_REMOTE:$R2_BUCKET/daily/" --files-only | LC_ALL=C sort | tail -1)"
    if [ -z "$latest" ]; then
        die "R2 daily/에 백업 파일이 없음"
    fi
    log "대상 아카이브: $latest"

    STEP="다운로드"
    WORK="$(mktemp -d "$BACKUP_DIR/.restore.XXXXXX")"
    rclone --config "$RCLONE_CONFIG_FILE" copyto "$R2_REMOTE:$R2_BUCKET/daily/$latest" "$WORK/$latest" --retries 3
    local sha_archive
    sha_archive="$(sha256sum "$WORK/$latest" | awk '{print $1}')"

    STEP="구성물 검증"
    tar -xzf "$WORK/$latest" -C "$WORK"
    local f
    for f in db.dump uploads.tar manifest.json; do
        if [ ! -f "$WORK/$f" ]; then
            die "아카이브에 $f 없음"
        fi
    done
    local sha_expected sha_actual
    for f in db.dump uploads.tar; do
        sha_expected="$(jq -r --arg f "$f" '.sha256[$f]' "$WORK/manifest.json")"
        sha_actual="$(sha256sum "$WORK/$f" | awk '{print $1}')"
        if [ "$sha_expected" != "$sha_actual" ]; then
            die "$f SHA-256 불일치 (manifest: $sha_expected / 실제: $sha_actual)"
        fi
    done

    STEP="임시 컨테이너 가동"
    docker rm -f -v "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
    local pg_pw
    pg_pw="$(openssl rand -hex 16)"
    docker run -d --name "$RESTORE_CONTAINER" --network none --memory 512m --security-opt no-new-privileges --pids-limit 256 \
        -e POSTGRES_DB=gakkaweo -e POSTGRES_PASSWORD="$pg_pw" postgres:16-alpine >/dev/null

    # TCP로 접속해 확인한다 (초기화 중에 뜨는 소켓 전용 임시 서버를 준비 완료로 오인하지 않도록)
    STEP="임시 컨테이너 준비 대기"
    local waited=0
    until docker exec "$RESTORE_CONTAINER" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1 &&
        docker exec -e PGPASSWORD="$pg_pw" "$RESTORE_CONTAINER" \
            psql -h 127.0.0.1 -U postgres -d gakkaweo -Atc 'SELECT 1' >/dev/null 2>&1; do
        if [ "$waited" -ge "$PG_READY_TIMEOUT" ]; then
            die "임시 PostgreSQL이 ${PG_READY_TIMEOUT}초 안에 준비되지 않음"
        fi
        sleep 2
        waited=$((waited + 2))
    done

    # --single-transaction: pg_restore는 에러가 나도 종료코드 0으로 끝날 수 있어, 전체 롤백으로 실패를 드러낸다
    # --no-owner --no-privileges: 덤프에 기록된 소유자 계정이 임시 컨테이너에는 없어서
    STEP="복원"
    docker cp "$WORK/db.dump" "$RESTORE_CONTAINER:/tmp/db.dump"
    # umask 077로 만들어진 파일이라, 호스트 권한은 그대로 두고 컨테이너 안에서만 소유권을 넘긴다
    docker exec "$RESTORE_CONTAINER" chown postgres:postgres /tmp/db.dump
    docker exec -u postgres "$RESTORE_CONTAINER" \
        pg_restore --single-transaction --no-owner --no-privileges -d gakkaweo /tmp/db.dump

    STEP="row count 대조"
    local table expected actual row_summary="" mismatches=()
    for table in members guess_history daily_sentences; do
        expected="$(jq -r --arg t "$table" '.row_counts[$t]' "$WORK/manifest.json")"
        actual="$(docker exec -u postgres "$RESTORE_CONTAINER" psql -d gakkaweo -Atc "SELECT count(*) FROM $table")"
        row_summary+="$table=$actual "
        if [ "$expected" != "$actual" ]; then
            mismatches+=("$table: 기대 $expected / 실제 $actual")
        fi
    done
    if [ "${#mismatches[@]}" -gt 0 ]; then
        die "row count 불일치 - $(printf '%s; ' "${mismatches[@]}")"
    fi

    STEP="이미지 정합성 대조"
    local raw_urls url path skipped=0 db_names=""
    raw_urls="$(docker exec -u postgres "$RESTORE_CONTAINER" \
        psql -d gakkaweo -Atc 'SELECT profile_url FROM members WHERE profile_url IS NOT NULL')"
    while IFS= read -r url; do
        if [ -z "$url" ]; then
            continue
        fi
        path="${url%%\?*}" # 뒤에 붙는 ?v=... 쿼리 제거
        case "$path" in
            /uploads/profiles/*) db_names+="${path##*/}"$'\n' ;;
            *) skipped=$((skipped + 1)) ;; # 우리 서버 경로가 아닌 값(외부 URL 등)은 대조에서 뺀다
        esac
    done <<<"$raw_urls"

    local db_sorted tar_listing tar_sorted missing orphan_count
    db_sorted="$(printf '%s' "$db_names" | LC_ALL=C sort -u)"
    # 목록 조회 실패(아카이브 손상)와 "프로필 이미지가 하나도 없음"(정상)을 구분한다
    tar_listing="$(tar -tf "$WORK/uploads.tar")" || die "uploads.tar 목록을 읽지 못함 - 아카이브 손상 의심"
    tar_sorted="$(printf '%s
' "$tar_listing" | grep '^uploads/profiles/' | grep -v '/$' | sed 's|.*/||' | LC_ALL=C sort -u || true)"
    missing="$(LC_ALL=C comm -23 <(printf '%s\n' "$db_sorted") <(printf '%s\n' "$tar_sorted") | sed '/^$/d')"
    orphan_count="$(LC_ALL=C comm -13 <(printf '%s\n' "$db_sorted") <(printf '%s\n' "$tar_sorted") | sed '/^$/d' | wc -l)"
    if [ -n "$missing" ]; then
        die "DB에는 있는데 uploads.tar에 없는 이미지 (최대 10개): $(printf '%s' "$missing" | head -10 | tr '\n' ' ')"
    fi
    if [ "$orphan_count" -gt 0 ]; then
        # 백업에만 있는 고아 파일은 정상적으로 생길 수 있다 (앱이 이미지 삭제 실패를 무시하므로)
        log "INFO: uploads.tar에만 있는 고아 이미지 ${orphan_count}개"
    fi

    STEP="완료 알림"
    local elapsed
    elapsed=$(($(date +%s) - start_epoch))
    notify_discord "$COLOR_SUCCESS" "복원 리허설 성공" "아카이브: $latest
아카이브 SHA-256: $sha_archive
검증 통과: 파일 구성 / SHA-256 / row count / 이미지 대조
row count: $row_summary
고아 이미지: ${orphan_count}개 (INFO) / 대조 제외(외부 URL 등): ${skipped}개
소요: ${elapsed}초"
    STEP="healthchecks 성공 ping"
    hc_ping success
    log "복원 리허설 완료: $latest (${elapsed}초)"
}

main "$@"
