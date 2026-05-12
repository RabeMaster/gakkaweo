# 인프라 의사결정

## 자체 홈서버

클라우드 비용을 쓸 여유가 없었고, (백수 이슈) 마침 남는 데스크탑(`Intel(R) Celeron(R) G4930 CPU @ 3.20GHz`, 4GB RAM, 128GB SSD)이 있었다.
`Ubuntu 24.04 LTS Server`로 밀어버리고 서버로 활용했다.

이미 여러 번 홈서버를 구성해본 경험이 있었고, 리눅스 명령어를 직접 치며 서버를 관리하는 과정 자체가 재밌고 유익한 경험이 될 거라고 판단했다.

네트워크 환경도 이미 준비가 되어 있었다. 벽에서 나오는 KT 회선을 스위치에 물려 각 PC에 공인 IP를 직접 할당받는 구조라, 공유기 포트포워딩 없이 방화벽 설정만으로 외부 접속이 가능했다.

또한 한국에 서버를 둠으로써 리전 차이로 인한 지연을 줄이고 싶었다. 다만 Cloudflare를 Full (strict) 모드로 걸면서 경유지가 늘어나 약간의 지연이 추가되긴 한다.

- OS: `Ubuntu 24.04.4 LTS (Server)`
- 스펙: `Intel(R) Celeron(R) G4930 CPU @ 3.20GHz`, `4GB RAM`, `128GB SSD`
- 네트워크: `KT 회선 → 스위치 → 공인 IP 직접 할당`
- 단일 서버에 모든 서비스 배포 (Frontend 정적 파일 + Docker Compose)

## 도메인

가비아에서 구매했다. `r4b2`라는 단어를 좋아해서 선택했고, `.com` 대비 `.xyz`가 가격이 훨씬 저렴했다.

- Frontend: `gakkaweo.r4b2.xyz`
- Backend API: `api.r4b2.xyz`

## Cloudflare

Cloudflare를 리버스 프록시로 사용한다. 선택한 이유는 크게 세 가지다.

1. **홈서버 IP 보호**: 프록시 모드로 실제 서버 IP를 숨겨, IP 노출을 통한 공격이나 위치 탐색을 방어한다.
2. **무료 SSL**: Let's Encrypt를 직접 설정하고 갱신 스크립트를 관리하는 대신, Cloudflare Origin Certificate로 편하게 HTTPS를 구성했다. 신뢰할 수 있는 인증 기관이 알아서 해주니 관리 부담이 없다.
3. **DDoS 방어**: 홈서버는 인프라 수준의 방어가 없으므로, Cloudflare의 기본 DDoS 보호를 무료로 활용한다.

### SSL 구성

- **Origin Certificate**: Cloudflare가 발급하는 인증서를 Nginx에 설치. 브라우저 ↔ Cloudflare는 Cloudflare 인증서, Cloudflare ↔ Origin은 Origin Certificate로 E2E 암호화
- **SSL Mode**: Full (strict) — Origin Certificate 검증 활성화
- **캐싱**: 정적 자산(JS/CSS/이미지)은 Cloudflare 엣지에서 캐시. HTML/API는 캐시하지 않음

## 보안

홈서버를 외부에 노출하므로, 보안과 안정성 조치를 적용했다.

### 네트워크 보안

- **SSH**: 기본 포트 변경 + 키 인증만 허용 (비밀번호 로그인 비활성화)
- **IP 은닉**: Cloudflare 프록시로 실제 서버 IP 노출 차단
- **IP 직접 접근 차단**: Nginx `default.conf`에서 IP나 미등록 도메인으로의 접속을 444(연결 끊기)로 처리. Cloudflare를 우회한 직접 접근을 차단
- **방화벽**: 필요한 포트(HTTP, HTTPS, SSH)만 개방

### 시스템 안정성

4GB RAM으로 PostgreSQL, Redis, AI Service, Backend를 모두 돌리기 때문에, 메모리 부족에 대비한 설정이 필수였다.

- **Swap**: 12GB 스왑 영역 확보. RAM이 부족해도 서비스가 즉시 죽지 않도록 버퍼 역할
- **Swappiness**: 60 → 10으로 낮춤. 가능한 한 물리 RAM을 우선 사용하고, 정말 부족할 때만 스왑을 사용
- **OOM 방어**: SSH 프로세스는 OOM Killer가 죽이지 못하도록 보호했다. 만약 SSH마저 죽으면 서버에 원격 접근 자체가 불가능해지기 때문
  > (키보드와 모니터 꼽기 귀찮기에...)
- **시스템 예약 메모리**: 약 300MB를 시스템 전용으로 예약. 일반 앱이 메모리를 다 먹어도 SSH 등 핵심 시스템 명령이 동작할 수 있도록 보장
- **자동 재부팅**: 커널이 30초간 프리즈/패닉 상태가 되면 자동으로 재부팅. Docker, Nginx, SSH 모두 부팅 시 자동 시작되도록 설정
- **Magic SysRq**: 키보드조차 먹지 않는 완전 프리즈 상태에서도 커널에 직접 재부팅 명령을 날릴 수 있도록 활성화
- **쿨러 제어**: BIOS에서 CPU 온도 기반 팬 속도 자동 조절 설정
  > 처음에는 쿨러를 최대로 돌려서 온도를 낮추면 좋겠다! 했지만, 자는데 너무 시끄러웠고, CPU도 생각보다 온도가 높지 않아서, 온도 기반으로 팬 속도를 조절하도록 설정했다.

## Nginx

호스트 OS에서 직접 실행하기로 했다.

Docker 안에 넣지 않은 이유는 아래와 같다.

- Frontend는 Vite 빌드 결과물(`dist/`)을 Nginx가 직접 서빙 — Docker 볼륨 마운트 없이 단순
- 설정 파일 3개로 역할이 명확히 분리됨

```
nginx/
├── gakkaweo.conf   # Frontend SPA — try_files로 정적 파일 서빙, fallback to index.html
├── api.conf        # Backend API — 리버스 프록시 + SSE 버퍼링 비활성화
└── default.conf    # IP 직접 접근 및 미등록 도메인 차단 (444 응답)
```

### SSE 프록시 설정

`/ranking/stream` 엔드포인트는 SSE(Server-Sent Events)를 사용한다. Nginx 기본 설정은 응답을 버퍼링하므로, SSE 전용 location 블록에서 버퍼링을 비활성화했다.

> 버퍼링을 켜놓는다면, 랭킹 업데이트가 발생해도 Nginx가 데이터를 버퍼링하여 클라이언트에 즉시 전달되지 않을 수 있다. 이로 인해 랭킹이 실시간으로 반영되지 않는 문제가 발생할 수 있다.

```nginx
location /ranking/stream {
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 86400s;  # 24시간 (SSE 연결 유지)
}
```

## Docker Compose

인프라 서비스(PostgreSQL, Redis, AI Service, Backend)는 Docker Compose로 관리한다.

### 개발 환경 (`docker-compose.dev.yml`)

- PostgreSQL 16, Redis 7, AI Service만 컨테이너로 실행
- Backend는 IDE에서 직접 실행 (디버깅/핫리로드)
- Frontend는 Vite dev server

> IDE에서 브레이크포인트를 걸고 디버깅하기 편하기도 했고, Backend는 자주 수정하면서 테스트하는 부분이 많아서, 컨테이너로 실행하지 않고 직접 실행하는 방식을 선택했다. 물론, 프로덕션에서는 컨테이너로 실행한다.

### 프로덕션 환경 (`docker-compose.prod.yml`)

- 4개 서비스 전부 컨테이너: PostgreSQL, Redis, AI Service, Backend
- Frontend는 CD에서 빌드 후 `dist/`를 SCP로 전송, Nginx가 직접 서빙
- Backend 포트는 `127.0.0.1:${SERVER_PORT}:8080`으로 localhost만 노출 (Nginx 프록시 경유)
- Redis: `maxmemory 256mb`, `volatile-lru` eviction, AOF 영속화
- AI Service: 메모리 1536MB 제한, 모델 캐시 볼륨 (`hf-model-cache`)
- 로그: json-file 드라이버, 10MB 로테이션 x 3 파일
- 재시작 정책: `unless-stopped`

> Redis에서 메모리가 부족해지면, `volatile-lru` 정책에 따라 TTL이 설정된 키 중에서 가장 오래된 것을 삭제한다. AI Service는 모델 캐시로 인해 메모리를 많이 사용할 수 있기 때문에, 1536MB로 제한했다. 만약 메모리 사용량이 이 한도를 초과하면, 재시작되게끔 설정했다.

> 로그가 쌓여서 디스크가 가득 차는 것을 방지하기 위해, json-file 드라이버로 로그를 저장하되, 10MB마다 파일을 롤링하고 최대 3개의 파일까지만 보관하도록 설정했다.

### 헬스체크 의존성 체인

```
PostgreSQL (pg_isready) ──┐
Redis (redis-cli ping)  ──┼── Backend (wget /health) ── 서비스 시작
AI Service (/health)    ──┘
```

Backend는 3개 서비스가 모두 healthy일 때만 시작된다. AI Service는 모델 로딩에 시간이 걸리므로 `start_period: 120s`를 설정했다.

## 모니터링 (Prometheus + Grafana)

홈서버 한 대에서 모든 서비스를 돌리다 보니, CPU/메모리/디스크 상태와 애플리케이션 내부 지표를 한 눈에 보고 싶었다. Spring Boot Actuator + Micrometer가 이미 메트릭 수집 인프라를 제공하므로, 여기에 Prometheus와 Grafana를 얹는 방식으로 구성했다.

### Actuator 포트 분리

Actuator 엔드포인트를 애플리케이션 포트(8080)가 아닌 별도 포트(9090)로 분리했다.

```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:9090}
  endpoints:
    web:
      exposure:
        include: prometheus
```

이렇게 하면 Nginx가 프록시하는 8080 포트에는 Actuator가 노출되지 않는다. 프로덕션에서는 Docker 네트워크 내부(internal)에서만 Prometheus가 9090에 접근하고, 외부에서는 접근할 수 없다.

### 커스텀 메트릭

Micrometer 기본 JVM/HTTP 메트릭 외에 도메인 메트릭을 추가했다.

| 이름                               | 타입    | 태그                     | 설명                                  |
| ---------------------------------- | ------- | ------------------------ | ------------------------------------- |
| `game.guesses.total`               | Counter | `result`=success/failure | 추측 시도 (정답/오답)                 |
| `game.clears.total`                | Counter | -                        | 클리어 횟수                           |
| `game.sentences.unused`            | Gauge   | -                        | 미사용 문장 잔량 (5분 주기 갱신)      |
| `ranking.update`                   | Counter | -                        | 랭킹 업데이트 횟수                    |
| `auth.login.total`                 | Counter | `provider`, `result`     | 로그인 시도 (프로바이더별, 성공/실패) |
| `auth.register.total`              | Counter | `provider`               | 회원가입                              |
| `auth.withdraw.total`              | Counter | -                        | 탈퇴                                  |
| `discord.webhook.total`            | Counter | `result`=success/failure | Discord 웹훅 전송                     |
| `ratelimit.rejected`               | Counter | `group`                  | Rate Limit 거부 횟수                  |
| `ratelimit.buckets.active`         | Gauge   | -                        | 활성 버킷 수                          |
| `sse.connections`                  | Gauge   | -                        | 활성 SSE 연결 수                      |
| `scheduler.midnight.duration`      | Timer   | -                        | 자정 스케줄러 실행 시간               |
| `ranking.cache.rebuild.duration`   | Timer   | -                        | 랭킹 캐시 리빌드 시간                 |
| `scheduler.redis_cleanup.duration` | Timer   | -                        | Redis 정리 스케줄러 실행 시간         |

Gauge는 `CustomMetricsConfig`에서 `MeterBinder`로 등록하고, Counter는 각 서비스에서 생성자 주입 시 빌더로 등록한다. `@Timed`는 `TimedAspect` 빈으로 활성화했다. 인증 메트릭은 `AuthMetrics` 클래스로 분리해서 프로바이더별 카운터를 일괄 초기화하는 패턴을 사용했다.

### Grafana 프로비저닝

Grafana 대시보드와 데이터소스를 코드로 관리한다. 컨테이너가 새로 생성되어도 수동 설정 없이 동일한 대시보드가 자동으로 로드된다.

```
infra/
├── grafana/
│   ├── dashboards/
│   │   └── gakkaweo.json         # 대시보드 정의
│   └── provisioning/
│       ├── dashboards/
│       │   └── dashboard.yml     # 대시보드 프로비저닝
│       └── datasources/
│           └── prometheus.yml    # 데이터소스 프로비저닝
└── prometheus/
    ├── prometheus.dev.yml        # 로컬 환경
    └── prometheus.prod.yml       # 프로덕션 환경
```

### 프로덕션 접근 제어

Prometheus와 Grafana 모두 외부에 직접 노출하지 않는다.

- Prometheus: Docker internal 네트워크에만 연결, 포트 바인딩 없음
- Grafana: `127.0.0.1:3001:3000`으로 localhost만 바인딩

대시보드를 볼 때는 SSH 터널(`ssh -L 3001:localhost:3001`)로 접근한다. Cloudflare 프록시를 통하지 않으므로 인증 우회나 외부 노출 걱정이 없다.

## CI/CD (GitHub Actions)

### CI — PR to dev

변경된 서비스만 검증한다 (`dorny/paths-filter`).

| 서비스     | 검증 항목                                                    |
| ---------- | ------------------------------------------------------------ |
| Frontend   | ESLint → Prettier format:check → Vite build                  |
| Backend    | Spotless check (Google Java Format) → Gradle build (-x test) |
| AI Service | Ruff check → Ruff format --check                             |

Branch Protection: 3개 job 모두 통과해야 dev 머지 가능.

### CD — Push to dev

변경 감지 → 병렬 빌드 → 조건부 배포.

```
변경 감지 (paths-filter)
    ├── Frontend 변경 → Vite build → SCP dist/ → Nginx 서빙
    ├── Backend 변경 → Docker build → GHCR push → SSH docker compose up
    ├── AI 변경 → Docker build → GHCR push → SSH docker compose up
    └── Nginx 변경 → SCP conf → SSH nginx reload
```

Docker 이미지는 GHCR(GitHub Container Registry)에 push하고, 서버에서 pull하는 방식이다. 서버에서 빌드하지 않음.

> 서버에서 빌드를 하면, 빌드 과정에서 CPU와 메모리를 많이 사용하게 되어, 서비스가 불안정해질 수 있다. 또한, 빌드 시간이 길어지면 배포가 지연되고, 문제가 발생했을 때 원인 파악이 어려워질 수 있다. 따라서, CI에서 미리 빌드된 이미지를 GHCR에 저장하고, 서버에서는 이를 pull하여 실행하는 방식을 선택했다.

## 환경 변수 관리

```
.env.sample        # 로컬 개발 템플릿 (루트)
.env.prod.sample   # 프로덕션 템플릿
.env               # 로컬 실제 값 (gitignore)
.env.prod          # 서버 실제 값 (서버에만 존재)
backend/.env       # 루트 .env의 심볼릭 링크
```

`.env`는 Docker Compose와 Spring Boot가 공용으로 사용한다. `backend/.env`는 루트의 심볼릭 링크로, 중복 관리를 방지한다.

> 처음에는 각 서비스별로 `.env` 파일을 따로 관리하려고 했지만, Docker Compose와 Spring Boot가 모두 환경 변수를 필요로 하기 때문에, 루트에서 하나의 `.env` 파일로 관리하는 것이 더 편리하다고 판단했다. 또한, `backend/.env`를 루트의 심볼릭 링크로 만들어서, 백엔드에서도 동일한 환경 변수를 사용할 수 있도록 했다.

---

_마지막 업데이트: 2026-05-12_
