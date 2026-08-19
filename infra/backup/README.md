# DB 백업 자동화

가까워 운영 데이터를 매일 자동으로 백업하고, 서버 밖(Cloudflare R2)에 보관하고, 매주 실제로 복원되는지 검증하고, 백업이 멈춘 상황까지 감시하는 스크립트 모음

| 파일 | 역할 | 실행 주기 |
| --- | --- | --- |
| `backup.sh` | DB 덤프, 프로필 이미지, manifest를 tar.gz 하나로 묶어 R2에 업로드 | 매일 04:00 KST |
| `restore-test.sh` | R2의 최신 백업을 임시 컨테이너에 실제로 복원해 manifest와 대조 | 매주 일요일 04:30 KST |
| `lib.sh` | 두 스크립트가 공유하는 공통 함수 (로그, 알림, 잠금, .env 파서) | 단독 실행 안 함 (source 전용) |

## 1. 개요

### 백업 대상

- **PostgreSQL** (`gakkaweo` DB): `pg_dump -Fc`로 뜬 덤프 파일
- **프로필 이미지**: 호스트의 `~/gakkaweo/uploads/` 디렉토리
- **manifest.json**: 백업 명세서. 생성 시각(KST), 테이블별 행 개수, 이미지 개수, 파일별 SHA-256 해시를 담는다

### 제외 대상과 이유

| 대상 | 제외 이유 |
|---|---|
| Redis | 유실을 허용하는 캐시 데이터입니다. 랭킹 등의 데이터는 복구 후 관리자 페이지의 '랭킹 캐시 리셋'으로 DB 기준 재생성합니다 (7장 복구 절차 참고). |
| Prometheus / Grafana 데이터 | 관측용 데이터이므로 유실되어도 서비스 운영에는 직접적인 영향이 없습니다. 대시보드와 설정은 `infra/`에 코드로 관리되어 재구성할 수 있습니다. |
| GHCR 이미지 | GitHub Container Registry가 원본이므로 필요할 경우 다시 pull하거나 빌드할 수 있습니다. |
| `.env.prod` | 시크릿 정보가 포함되어 있으므로 백업 아카이브에는 포함하지 않습니다. 별도로 안전하게 보관합니다. |

> **경고: `.env.prod`는 이 백업에 포함되지 않습니다.**  
> DB 비밀번호, JWT 시크릿, OAuth 키 등의 시크릿은 개인 비밀번호 관리자 등에 별도로 보관해야 합니다.  
> 서버와 R2를 모두 잃은 상황에서 `.env.prod`까지 복구하지 못하면, DB 백업이 남아 있더라도 애플리케이션을 정상적으로 가동할 수 없습니다.

### 보관 정책 요약

백업 파일은 하루에 1개 만들어지고 항상 `daily/`에 업로드.  
주간/월간 백업을 따로 뜨는 것이 아니라, 그날이 일요일이면 같은 파일을 `weekly/`에, 매월 1일이면 `monthly/`에 한 벌 더 복사해 둔다.  
`daily/`가 60일 뒤에 지워져도 일요일분은 6개월, 매월 1일분은 2년동안 남는다.  
비교적 최근은 하루 단위로 촘촘하게, 오래된 과거는 주/월 단위로 듬성듬성하게 복구 시점을 남겨서 저장 용량을 아끼는 방식을 택함.

| 위치 | 경로 | 무엇이 쌓이나 | 보관 기간 | 누가 지우는가 |
| --- | --- | --- | --- | --- |
| 서버 로컬 | `~/gakkaweo/backups/` | 매일 백업 원본 | 7일 | `backup.sh` |
| R2 | `daily/` | 매일 백업 | 60일 | R2 lifecycle 규칙 |
| R2 | `weekly/` | 일요일 백업의 복사본 (주 1개) | 182일 | R2 lifecycle 규칙 |
| R2 | `monthly/` | 매월 1일 백업의 복사본 (월 1개) | 730일 | R2 lifecycle 규칙 |

암호화는 하지 않는다 (필요해지면 추후 추가).

## 2. 구조

### 실행 흐름

```
backup.sh (매일 04:00 KST)
  잠금 획득 (앞선 백업/리허설이 아직 돌고 있으면 중복 실행하지 않고 알림 없이 종료)
  -> 사전 점검 (필수 명령, DB 컨테이너, 디스크 여유)
  -> healthchecks에 시작 신호
  -> pg_dump로 DB 덤프 -> 크기 확인 + 목록 조회로 손상여부 체크
  -> 테이블 행 개수 기록 -> 프로필 이미지 tar -> manifest.json 작성
  -> 셋을 tar.gz 하나로 묶기 -> R2 daily/ 업로드
     (일요일이면 weekly/, 매월 1일이면 monthly/에도 복사)
  -> 로컬에서 7일 지난 백업 삭제
  -> Discord 성공 알림 + healthchecks에 성공 신호

restore-test.sh (매주 일요일 04:30 KST)
  잠금 획득 (백업이 돌고 있으면 끝날 때까지 최대 30분 대기)
  -> R2 daily/의 최신 백업 다운로드 -> 파일별 SHA-256 재확인
  -> 임시 postgres:16-alpine 컨테이너 띄우기 (네트워크 차단)
  -> pg_restore로 DB 복원
  -> 테이블 행 개수가 manifest와 정확히 일치하는지 확인 (오차 0인지)
  -> 프로필 이미지 목록이 DB 기록과 맞는지 대조
  -> Discord 성공 알림 + healthchecks에 성공 신호 -> 임시 컨테이너와 작업 파일 정리
```

### 아카이브 구성

```
gakkaweo_YYYYMMDD_HHMMSS.tar.gz     # 파일명 시각은 KST
├── db.dump                          # pg_dump -Fc
├── uploads.tar                      # ~/gakkaweo/uploads (상대경로 uploads/...)
└── manifest.json
```

파일명에는 날짜+시각으로 된 타임스탬프가 들어가서, R2 버킷에 같은 이름으로 덮어쓰는 일이 없도록 한다.  
R2 버킷 잠금이 같은 이름으로 덮어쓰는 것을 거부하기 때문에, 같은 날 다시 실행해도 이름이 겹치지 않아야 하기 때문.

### manifest.json 스키마

```json
{
  "created_at": "2026-08-19T04:00:03+09:00",
  "archive": "gakkaweo_20260819_040003.tar.gz",
  "row_counts": { "members": 0, "guess_history": 0, "daily_sentences": 0 },
  "image_count": 0,
  "sha256": { "db.dump": "...", "uploads.tar": "..." }
}
```

## 3. 최초 1회 준비

### 3-1. 서버 패키지 설치

```bash
sudo apt-get update && sudo apt-get install -y jq curl tar coreutils util-linux openssl rclone
rclone version   # apt 버전으로 충분. 더 최신이 필요할 때만 rclone.org의 공식 설치 방법 사용
```

### 3-2. R2 버킷 생성 + bucket lock

Cloudflare 대시보드 -> R2 -> Create bucket -> 이름 `gakkaweo-backup` (location 자동).

**bucket lock 설정 (필수)**: 버킷 Settings -> Bucket lock에 경로별 규칙 3개를 등록한다. 잠금 기간을 보관 기간과 같게 걸어서, 보관하는 동안에는 누구도 지우거나 덮어쓸 수 없게 한다.

| 경로 | 잠금 기간 |
| --- | --- |
| `daily/` | 60일 |
| `weekly/` | 182일 |
| `monthly/` | 730일 |

서버가 해킹당해 토큰이 탈취돼도 보관 중인 백업만은 살아남게 하기 위함이다.  
단, Cloudflare 계정 관리자는 대시보드에서 잠금 규칙 자체를 해제할 수 있다. 이 방어는 계정 로그인이 안전하다는 전제 위에 있다.

### 3-3. lifecycle 규칙

버킷 Settings -> Object lifecycle rules에 경로별로 3개 등록:

| 경로 | 만료 |
| --- | --- |
| `daily/` | 60일 후 삭제 |
| `weekly/` | 182일 후 삭제 |
| `monthly/` | 730일 후 삭제 |

경로별 만료 기간이 잠금 기간과 같으므로, 잠금이 풀리는 시점에 lifecycle이 지운다.  
**만료 기간을 잠금 기간보다 짧게 줄이면 안 된다.**  
잠금이 삭제를 거부해서, 실제 삭제가 잠금이 풀릴 때까지 미뤄지기만 한다.

### 3-4. API 토큰 2개 발급

R2 -> Manage R2 API Tokens:

| 토큰 | 권한 | 보관 위치 |
| --- | --- | --- |
| 서버용 | **Object Read & Write, `gakkaweo-backup` 버킷 한정** | 서버 rclone 설정 |
| 복구용 | Admin Read & Write | 개인 비밀번호 관리자. **서버에 두지 않는다** |

서버용 토큰은 이 버킷에만 접근할 수 있어서, 서버가 해킹당해도 계정의 다른 리소스에는 손댈 수 없다.  
보관 기간 중인 백업을 지우는 것도 bucket lock이 막는다.

### 3-5. rclone 설정

`~/.config/rclone/rclone.conf`:

```ini
[r2]
type = s3
provider = Cloudflare
access_key_id = <서버용 토큰 Access Key ID>
secret_access_key = <서버용 토큰 Secret Access Key>
endpoint = https://<계정ID>.r2.cloudflarestorage.com
region = auto
no_check_bucket = true
```

토큰 발급 화면에는 Cloudflare API용 "Token value"와 S3용 "Access Key ID / Secret Access Key"가 함께 표시된다.  
**rclone에 넣는 것은 S3용 키 쌍이다** (Token value를 넣으면 인증 실패).  
Secret Access Key는 발급 화면에서 한 번만 보여주므로 그 자리에서 복사한다.  
endpoint의 계정 ID도 같은 화면 하단에 S3 API 주소로 표시된다.

**`no_check_bucket = true` 필수**: 버킷 한정 토큰에는 버킷 목록을 조회할 권한이 없다.  
> 이 옵션이 없으면 rclone이 업로드 전에 버킷부터 확인하려다 403으로 실패한다.

설정 파일 권한을 좁히고 연결 테스트:

```bash
chmod 700 ~/.config/rclone && chmod 600 ~/.config/rclone/rclone.conf
rclone lsd r2:gakkaweo-backup        # 에러 없이 끝나면 성공 (빈 버킷이면 출력 없음)
```

### 3-6. healthchecks.io 체크 2개 생성

[healthchecks.io](https://healthchecks.io) 무료 플랜으로 체크 2개를 만들고 ping URL을 받는다:

| 체크 | Schedule | Grace Time |
| --- | --- | --- |
| gakkaweo-backup | 매일 04:00 KST (cron `0 4 * * *`, TZ Asia/Seoul) | 30분 |
| gakkaweo-restore-test | 일요일 04:30 KST (cron `30 4 * * 0`, TZ Asia/Seoul) | 60분 |

매일 와야 할 성공 신호가 정시에 안 오면 healthchecks.io가 대신 알려준다.  
cron 자체가 죽어서 실패 알림조차 못 보내는 상황을 잡기 위한 장치다.  
스크립트가 보내는 ping은 출석 도장일 뿐이고, 도장이 안 찍혔을 때 어디로 알릴지는 아래 Integrations에 등록해야 한다.

**Discord 웹훅으로 알림 받기** (Integrations -> Webhook -> Add Integration):

- Execute on "down" events:
  - URL: 기존 Discord 웹훅 URL
  - Request Method: `POST`
  - Request Body: `{"content": "🔴 [$NAME] cron 신호가 오지 않았습니다."}`
  - Request Headers: `Content-Type: application/json`
- Execute on "up" events (선택): `{"content": "🟢 [$NAME] cron 신호가 다시 들어옵니다."}`
- `$NAME`은 healthchecks.io가 체크 이름으로 치환하는 변수다
- 저장 후 **체크 2개 각각에 이 integration이 켜져 있는지 확인**한다 (체크별로 붙어 있어야 발동)

Discord까지 먹통인 경우를 대비해 이메일 알림도 하나 설정 해 둔다.

### 3-7. .env.prod 변수 추가

`.env.prod.sample`의 백업 섹션을 참고해 서버의 `.env.prod`에 추가한다:

```
BACKUP_HEALTHCHECK_URL=https://hc-ping.com/<uuid1>
RESTORE_TEST_HEALTHCHECK_URL=https://hc-ping.com/<uuid2>
BACKUP_R2_REMOTE=r2
BACKUP_R2_BUCKET=gakkaweo-backup
```

`DISCORD_WEBHOOK_URL`, `DISCORD_MENTION_ROLE_ID`는 기존 값을 그대로 재사용한다.

### 3-8. Cloudflare billing 알림

Cloudflare 대시보드 -> Notifications에서 R2 사용량/과금 알림을 켠다. healthchecks.io까지 같이 먹통이 되는 경우를 대비한 별도 감시 채널이다.

## 4. 설치와 첫 실행

스크립트는 CD가 `~/gakkaweo/infra/backup/`에 배포한다 (6장 참고). 첫 검증 순서:

```bash
# 1) rclone 연결 확인
rclone lsd r2:gakkaweo-backup

# 2) R2 업로드만 빼고 전 과정 확인 (덤프, 검증, 묶기, Discord 알림. healthchecks 신호는 안 보냄)
BACKUP_SKIP_UPLOAD=1 /bin/bash ~/gakkaweo/infra/backup/backup.sh

# 3) 전체 실행
/bin/bash ~/gakkaweo/infra/backup/backup.sh

# 4) 확인: Discord 성공 메시지, healthchecks ping 기록, R2에 올라간 파일
rclone lsf r2:gakkaweo-backup/daily/

# 5) 복원 리허설 1회
/bin/bash ~/gakkaweo/infra/backup/restore-test.sh

# 6) 임시 컨테이너와 볼륨이 정리됐는지 확인
docker ps -a | grep restore-test || echo "컨테이너 정리 OK"
docker volume ls -q | wc -l      # 리허설 전후 개수가 같아야 함
```

전부 통과하면 crontab을 등록하고(5장), 다음 날 04:00 KST 결과를 확인한다.

## 5. cron 등록

**호스트 타임존부터 확인한다** (`timedatectl`). 스크립트 안의 시각 계산은 전부 KST로 고정해 뒀지만, cron이 스크립트를 깨우는 시각만은 호스트 타임존을 따르기 때문이다.

| 호스트 타임존 | 백업 (04:00 KST) | 리허설 (일요일 04:30 KST) |
| --- | --- | --- |
| Asia/Seoul | `0 4 * * *` | `30 4 * * 0` |
| UTC | `0 19 * * *` | `30 19 * * 6` (**토요일**) |

> **함정**: UTC 호스트에서 리허설을 `30 19 * * 0`(일요일 UTC)으로 걸면 실제로는 **월요일 04:30 KST**에 돈다. UTC 토요일 19:30이 KST 일요일 04:30이다.

compose를 돌리는 사용자(docker 그룹 소속)의 crontab에 등록한다.  
root의 crontab에 넣으면 `$HOME`이 다른 곳을 가리켜서 안 된다.  
crontab 안에서 `%` 문자는 특수하게 해석되므로 쓰지 않는다.

```cron
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
MAILTO=""

# 가까워 DB 백업 (04:00 KST) / 복원 리허설 (일요일 04:30 KST) - UTC 호스트 기준
0 19 * * * /bin/bash $HOME/gakkaweo/infra/backup/backup.sh >> $HOME/gakkaweo/backups/backup.log 2>&1
30 19 * * 6 /bin/bash $HOME/gakkaweo/infra/backup/restore-test.sh >> $HOME/gakkaweo/backups/restore-test.log 2>&1
```

logrotate (`/etc/logrotate.d/gakkaweo-backup`):

```
/home/<사용자>/gakkaweo/backups/*.log {
    monthly
    rotate 6
    compress
    missingok
    notifempty
    create 0600 <사용자> <그룹>
    su <사용자> <그룹>
}
```

## 6. 운영

### 알림 읽는 법

| 채널 | 의미 |
| --- | --- |
| Discord 초록 embed | 성공. 파일명, 크기, SHA-256, 행 개수, R2 사용량 포함 |
| Discord 노랑 embed | 성공했지만 경고 있음 (weekly/monthly 복사 실패, 사용량 측정 실패 등) 또는 R2 사용량 8GB 초과 |
| Discord 빨강 embed | 실패. 실패한 단계, 종료코드, 진행 로그 끝부분 포함. 외부 명령(pg_dump 등)의 상세 출력은 서버 로그 파일에만 남는다 |
| healthchecks 알림 | 정시에 성공 신호가 안 왔다는 뜻. cron이 죽었거나, 서버가 내려갔거나, 스크립트가 멈춰 있는 경우. 서버 로그부터 확인한다 |

리허설 성공 알림에 찍히는 아카이브 SHA-256은 그날 백업 성공 알림의 값과 같아야 정상이다.  
눈으로 비교해 확인할 수 있다.

### 수동 실행

```bash
/bin/bash ~/gakkaweo/infra/backup/backup.sh          # 파일명에 시각이 들어가서 다시 돌려도 충돌 없음
/bin/bash ~/gakkaweo/infra/backup/restore-test.sh    # 운영 DB를 건드리지 않음
```

두 스크립트는 잠금을 공유한다.  
백업 시각(04:00 KST) **직전**에는 리허설 수동 실행을 피한다.  
04:00에 cron 백업이 뜨는 순간 리허설이 잠금을 쥐고 있으면, 백업은 기다리지 않고 알림 없이 종료해서 그날 백업이 빠진다.  
반대로 백업이 이미 돌기 시작한 뒤에 리허설을 돌리는 것은 괜찮다 (리허설은 백업이 끝날 때까지 기다렸다가 진행).

### 설정 바꾸기

스크립트를 고치지 않고도 아래 환경변수로 기본값을 바꿀 수 있다:

| 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `GAKKAWEO_HOME` | `$HOME/gakkaweo` | 서비스 루트 |
| `BACKUP_DIR` | `$GAKKAWEO_HOME/backups` | 로컬 보관/작업 디렉토리 |
| `BACKUP_SKIP_UPLOAD` | `0` | `1`이면 R2 업로드와 healthchecks 신호 생략 (설치 검증용) |
| `BACKUP_RETENTION_MTIME` | `6` | 로컬 보관 기간 (find -mtime 값, 6 = 7일) |
| `BACKUP_MIN_DUMP_BYTES` | `102400` | 덤프 최소 크기 (이보다 작으면 실패 처리) |
| `BACKUP_QUOTA_WARN_BYTES` | `8000000000` | R2 사용량 경고 기준 (8GB, 무료 10GB의 80%) |
| `RESTORE_LOCK_WAIT_SECONDS` | `1800` | 리허설이 잠금을 기다리는 최대 시간 |
| `RESTORE_PG_READY_TIMEOUT` | `60` | 임시 PostgreSQL 컨테이너를 기다리는 최대 시간 |
| `BACKUP_MIN_FREE_KB` | `1000000` | 백업 전 디스크 여유 하한 (약 1GB, df 블록 수) |
| `RESTORE_MIN_FREE_KB` | `1000000` | 리허설 전 디스크 여유 하한 (약 1GB, df 블록 수) |
| `ENV_FILE` | `$GAKKAWEO_HOME/.env.prod` | 변수 파일 위치 |
| `RCLONE_CONFIG_FILE` | `~/.config/rclone/rclone.conf` | rclone 설정 파일 위치 |

### 용량 관리

매일 성공 알림에 R2 사용량이 찍히고, 8GB를 넘으면 별도로 경고가 온다.  
경고가 오면 lifecycle 규칙이 제대로 도는지, 백업 크기가 갑자기 커지지 않았는지 확인하고, 필요하면 daily 보관 기간을 줄인다.  
> (단, 30일보다 짧게는 금지 - 3-3장).

### 스크립트 수정 시 반영 절차

`.github/workflows/cd.yml`의 paths-filter에 `infra/**`가 있어서 **main에 머지하면 `~/gakkaweo/infra`가 자동으로 배포된다.** 서버에 손으로 복사할 필요가 없다.

- 반영 경로: dev 머지 -> main 머지 -> CD가 `~/gakkaweo/infra`로 SCP
- **주의: CD는 배포 전에 대상 디렉토리를 통째로 지우고 다시 올린다 (`rm: true`).** 그래서 락 파일, 로그, 작업 파일, rclone 설정 같은 서버 로컬 상태를 `~/gakkaweo/infra/` 아래에 절대 두지 않는다. 전부 `~/gakkaweo/backups/` 또는 `~/.config/rclone/`에 있다
- 급하게 손으로 반영해야 하면: `scp infra/backup/*.sh gakkaweo:~/gakkaweo/infra/backup/` 후 실행 권한 확인

## 7. 복구 가이드북

### 판단 기준

- 데이터가 오염되거나 지워짐, 볼륨 유실, 서버 자체 유실 -> 전체 복구
- 특정 테이블이나 일부 행만 되살리면 됨 -> 부분 복구

### 전체 복구

```bash
# 0) 복구할 아카이브 선택
rclone lsf r2:gakkaweo-backup/daily/    # weekly/, monthly/ 목록도 확인

# 1) 다운로드 + 해시 검증 (덤프에 개인정보가 있으므로 권한을 조인다)
umask 077
install -d -m 700 ~/recovery && cd ~/recovery
rclone copyto r2:gakkaweo-backup/daily/<아카이브명> ./<아카이브명>
tar -xzf <아카이브명>
sha256sum db.dump uploads.tar    # manifest.json에 적힌 sha256 값과 대조

# 2) 서비스 중지
cd ~/gakkaweo && docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend

# 3) DB를 비우고 복원 (기존 데이터 위에 덮으면 충돌이 나므로 DB를 새로 만든다)
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgresql \
  sh -c 'dropdb -U "$POSTGRES_USER" gakkaweo && createdb -U "$POSTGRES_USER" gakkaweo'
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgresql \
  sh -c 'pg_restore -U "$POSTGRES_USER" --single-transaction --no-owner --no-privileges -d gakkaweo' \
  < ~/recovery/db.dump

# 4) 프로필 이미지 복원 (풀기 전에 압축 안에 uploads/ 말고 다른 것이 없는지 확인)
tar -tf ~/recovery/uploads.tar | grep -v '^uploads/' || echo "파일 목록 정상 (uploads/ 뿐)"
tar -xf ~/recovery/uploads.tar -C ~/gakkaweo    # uploads/ 폴더로 풀린다

# 5) Redis 초기화 (복구 시점과 어긋난 랭킹/캐시 제거. 비밀번호는 .env.prod의 REDIS_PASSWORD 값)
docker compose -f docker-compose.prod.yml --env-file .env.prod exec redis redis-cli -a '<REDIS_PASSWORD>' FLUSHALL

# 6) 가동
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d

# 7) 랭킹 재구축: 어드민(/admin) 시스템 탭에서 "랭킹 캐시 리셋" 실행
#    재가동만으로는 당일 랭킹이 다시 만들어지지 않는다 (시작 시점 스케줄러는 문장 선정만 확인)

# 8) 복구 작업물 정리 (암호화되지 않은 덤프를 서버에 남기지 않는다)
rm -rf ~/recovery
```

서버 자체를 잃었다면: 새 서버에 Docker/compose 구성 -> `.env.prod`를 비밀번호 관리자에서 복원 -> GHCR 이미지 pull -> 위 절차의 1), 3), 4), 6), 7), 8). (새 서버는 Redis가 애초에 비어 있어 5는 생략)

### 부분 복구

임시 컨테이너에 복원해 놓고 필요한 데이터만 뽑아낸다:

```bash
# 이 컨테이너에 포트 매핑(-p)을 붙이지 않는다
PW="$(openssl rand -hex 16)"
docker run -d --name recovery-db --network none --memory 512m \
  -e POSTGRES_DB=gakkaweo -e POSTGRES_PASSWORD="$PW" postgres:16-alpine
docker cp ~/recovery/db.dump recovery-db:/tmp/db.dump
docker exec -u postgres recovery-db pg_restore --single-transaction --no-owner --no-privileges -d gakkaweo /tmp/db.dump
docker exec -u postgres recovery-db psql -d gakkaweo -c "COPY (SELECT ...) TO STDOUT WITH CSV HEADER" > extract.csv
docker rm -f -v recovery-db && rm -rf ~/recovery
```

### R2 접근 불가 시 (서버용 토큰 유실/폐기)

개인 비밀번호 관리자에 있는 **복구용 Admin 토큰**을 쓰되, **운영 서버가 아닌 별도 PC에서만** 쓴다. 서버 토큰이 사라진 상황은 침해가 의심되는 상황일 수 있고, 그 서버에 Admin 토큰을 올리면 백업 전체를 지울 권한까지 내주는 셈이기 때문이다.  

별도 PC에 rclone remote를 임시로 만들어 다운로드하고, 필요한 파일만 서버로 옮긴다. 사용이 끝나면 Cloudflare 대시보드에서 그 Admin 토큰을 **폐기(revoke)하고 새로 발급**해 둔다 (rclone 설정을 지우는 것만으로는 토큰이 살아 있다). 서버용 토큰도 재발급한다.

### 복구 후 체크리스트

- [ ] 로그인 (로컬 + 소셜)
- [ ] 오늘의 문장 조회, 추측 1회, 랭킹 갱신
- [ ] 프로필 이미지 표시
- [ ] Flyway 버전: `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;`
- [ ] 다음 날 백업 성공 알림 확인

## 8. 복원 리허설

`restore-test.sh`가 매주 검증하는 항목:

1. R2에서 실제 파일 다운로드 (아카이브 SHA-256을 알림에 기록)
2. 묶음 안에 파일 3개가 다 있는지 + 파일별 SHA-256이 manifest 기록과 같은지
3. 임시 컨테이너에 실제 복원 (일부만 실패해도 전체가 되돌려지고 실패로 처리된다)
4. 테이블 행 개수가 manifest와 정확히 일치하는지 (오차 0)
5. 이미지 대조: DB에 기록된 이미지 파일명이 백업 안에 다 있는지 확인. DB에는 있는데 백업에 없으면 실패. 백업에만 있는 파일은 참고 표시만 한다 (앱이 이미지 삭제 실패를 무시하는 구조라 정상적으로 생길 수 있다)

### 실패 진단 순서

1. Discord 실패 알림에서 실패한 단계와 로그 끝부분 확인
2. `~/gakkaweo/backups/restore-test.log` 전체 확인
3. 행 개수 불일치가 **guess_history만 1~2 차이**라면, 덤프 뜨는 시점과 개수 세는 시점 사이에 새 추측이 들어왔을 가능성이 있다. 한 번이면 다음 주 결과를 지켜보고, **반복되면 결함으로 취급**한다
4. SHA-256 불일치는 업로드/다운로드 중 손상이거나 변조다. 즉시 다른 날짜 아카이브도 검증한다

> **TODO**: 3번의 경합은 원리적으로 없앨 수 있다.  
> psql 트랜잭션에서 `pg_export_snapshot()`으로 스냅샷을 만들고, 같은 트랜잭션에서 행 개수를 센 뒤 `pg_dump --snapshot=<이름>`으로 뜨면 덤프와 개수가 정확히 같은 시점에 고정된다.  
> 지금은 백업이 트래픽 없는 새벽 4시에 돌고 틈도 1초 미만이라 보류. 거짓 경보가 반복되면 도입.

## 9. 제약과 주의사항

- **CD의 `rm: true`**: `~/gakkaweo/infra/`는 main에 머지할 때마다 통째로 교체된다. 서버 로컬 상태를 여기에 두지 않는다 (6장)
- **공통 함수는 `lib.sh`에 있다**: 두 스크립트가 시작할 때 같은 폴더의 `lib.sh`를 불러온다(source). `lib.sh`는 단독 실행용이 아니며, 세 파일이 항상 같이 배포되어야 한다 (CD가 폴더째 올리므로 평소에는 신경 쓸 일 없음)
- **줄바꿈은 LF**: `.gitattributes`가 `*.sh text eol=lf`를 강제한다. Windows 줄바꿈(CRLF)이 섞이면 서버에서 `bad interpreter` 에러로 죽는다
- **백업 중 서비스 영향 없음**: `pg_dump`는 그 시점의 스냅샷을 읽기만 해서 서비스를 막지 않고, 리허설은 운영 DB를 아예 건드리지 않는다
- **`.env.prod`는 셸 명령(source)으로 통째로 불러오면 안 된다**: JAVA_OPTS처럼 값에 공백이 있는 줄에서 스크립트가 죽는다. 그래서 스크립트는 필요한 값만 골라 읽는 자체 함수(`read_env`)를 쓴다. 스크립트를 고칠 때도 이 방식을 유지한다

## 10. 트러블슈팅

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| `bad interpreter: /bin/bash^M` | Windows 줄바꿈(CRLF) 유입 | `.gitattributes` 확인, `sed -i 's/\r$//' *.sh` 후 재커밋 |
| `rclone: command not found` (cron에서만) | cron 기본 PATH에 `/usr/local/bin`이 없음 | crontab의 `PATH=` 라인 확인 (스크립트도 자체 설정함) |
| rclone 403 AccessDenied (ListBuckets) | 버킷 한정 토큰인데 `no_check_bucket` 누락 | rclone.conf에 `no_check_bucket = true` 추가 |
| 업로드 409/412 (덮어쓰기 거부) | bucket lock이 같은 이름 재업로드를 거부 | 정상 동작. 파일명에 시각이 있어 재실행하면 새 이름으로 올라감 |
| `dial unix /var/run/docker.sock: permission denied` | crontab 사용자가 docker 그룹이 아님 | `sudo usermod -aG docker <사용자>` 후 재로그인 |
| 리허설이 30분 기다리다 실패 | backup.sh가 잠금을 오래 붙잡고 있음 (멈춤) | backup 로그 확인, 멈춘 프로세스 kill 후 수동 재실행 |
| `pg_restore: error: could not execute query` | 덤프와 PostgreSQL 버전 불일치 등 | postgres:16 계열인지 확인, 다른 날짜 아카이브로 재시도 |
| healthchecks는 "down"인데 Discord 알림 없음 | cron 자체가 안 돎 (서버 재부팅 후 cron 미가동 등) | `systemctl status cron`, `crontab -l` 확인 |
