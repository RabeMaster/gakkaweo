#!/usr/bin/env bash
# backup.sh / restore-test.sh 가 공유하는 공통 함수 모음. 단독 실행이 아니라 source 해서 쓴다.
# 함수들은 불러오는 쪽 스크립트가 정의한 전역값을 사용한다:
#   SCRIPT_NAME, SCRIPT_TITLE, ENV_FILE, LOCK_FILE, NOTIFY_LOG, HC_URL, DISCORD_WEBHOOK_URL

# Discord embed 색상 (10진수. 성공 0x57F287 / 경고 0xFEE75C / 실패 0xED4245)
COLOR_SUCCESS=5763719
COLOR_WARN=16705372
COLOR_FAIL=15548997
STEP="초기화"
NOTIFIED_FAILURE=0

# stderr로 출력한다 (stdout은 덤프 저장에 쓰인다). 실패 알림에 실을 사본은 NOTIFY_LOG에도 남긴다
log() {
    local msg
    msg="$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S') [$SCRIPT_NAME] $*"
    printf '%s\n' "$msg" >&2
    printf '%s\n' "$msg" >>"$NOTIFY_LOG" 2>/dev/null || true
}

# .env.prod에서 지정한 키만 골라 읽는 파서 (source 금지 - 값에 공백이 있는 줄에서 죽는다)
# 사용법: read_env KEY [기본값] / 우선순위: 프로세스 env > .env.prod > 기본값
read_env() {
    local key="$1" default="${2-}" from_env line value
    from_env="$(printenv "$key" || true)"
    if [ -n "$from_env" ]; then printf '%s' "$from_env"; return 0; fi
    if [ -f "$ENV_FILE" ]; then
        while IFS= read -r line || [ -n "$line" ]; do
            line="${line%$'\r'}" # CRLF 방어
            case "$line" in
                "$key="*)
                    value="${line#"$key"=}"
                    if [ "${#value}" -ge 2 ]; then # 값 전체를 감싼 따옴표만 제거
                        case "$value" in
                            \"*\") value="${value#\"}"; value="${value%\"}" ;;
                            \'*\') value="${value#\'}"; value="${value%\'}" ;;
                        esac
                    fi
                    printf '%s' "$value"
                    return 0
                    ;;
            esac
        done <"$ENV_FILE"
    fi
    printf '%s' "$default"
}

require_cmd() {
    local cmd
    for cmd in "$@"; do
        command -v "$cmd" >/dev/null 2>&1 || die "필수 명령을 찾을 수 없음: $cmd"
    done
}

# 잠금 파일을 FD 9로 열고 flock을 건다. 파일 열기 실패(권한/디스크 문제)는 잠금 경합과 구분해 즉시 실패 처리
acquire_lock() { exec 9>"$LOCK_FILE" || die "잠금 파일 열기 실패: $LOCK_FILE"; flock "$@" 9; }

# healthchecks.io ping. 사용법: hc_ping [start|fail|success]. 실패해도 스크립트를 죽이지 않는다
hc_ping() {
    local kind="${1:-success}" url="$HC_URL"
    if [ -z "$url" ]; then return 0; fi
    case "$kind" in start | fail) url="$url/$kind" ;; esac
    curl -fsS -m 10 --retry 3 -o /dev/null --url "$url" 2>/dev/null || log "healthchecks ping 실패(무시): $kind"
}

# Discord 알림: notify_discord <color> <title> <description> [mention_role_id]
# 실패해도 스크립트를 죽이지 않으며, 시크릿(웹훅 URL)은 로그에 남기지 않는다
notify_discord() {
    local color="$1" title="$2" description="$3" mention="${4:-}" content="" payload
    if [ -z "$DISCORD_WEBHOOK_URL" ]; then log "Discord webhook 미설정 - 알림 스킵: $title"; return 0; fi
    if [ -n "$mention" ]; then content="<@&${mention}>"; fi
    payload="$(jq -n --arg content "$content" --arg title "$title" --arg desc "$description" --argjson color "$color" \
        '{content: $content, embeds: [{title: $title, description: $desc, color: $color}]}' 2>/dev/null)" ||
        { log "Discord 페이로드 생성 실패(무시): $title"; return 0; }
    curl -fsS -m 10 -H 'Content-Type: application/json' -d "$payload" --url "$DISCORD_WEBHOOK_URL" >/dev/null 2>&1 ||
        log "Discord 알림 전송 실패(무시): $title"
}
# FATAL 경로: healthchecks /fail -> Discord 실패 알림. 호출부(die/on_error)가 종료 처리
fail_notify() {
    local exit_code="$1" reason="$2" log_tail="(로그 없음)"
    if [ "$NOTIFIED_FAILURE" = 1 ]; then return 0; fi
    NOTIFIED_FAILURE=1
    hc_ping fail
    if [ -f "$NOTIFY_LOG" ]; then log_tail="$(tail -c 800 "$NOTIFY_LOG" 2>/dev/null || true)"; fi
    notify_discord "$COLOR_FAIL" "$SCRIPT_TITLE 실패" "단계: $STEP
종료코드: $exit_code
사유: $reason
\`\`\`
$log_tail
\`\`\`"
}

die() {
    log "FATAL: [$STEP] $*"
    fail_notify 1 "$*"
    exit 1
}

on_error() {
    if [ "$BASHPID" != "$$" ]; then return 0; fi # 서브셸 안에서 난 에러는 부모 쪽에서 한 번만 알린다
    local lineno="$1" exit_code="$2"
    log "ERROR: ${lineno}행에서 명령 실패 (종료코드 $exit_code)"
    fail_notify "$exit_code" "${lineno}행 명령 실패"
}
