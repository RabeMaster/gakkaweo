# Backend REST API 명세

## 개요

- **Base URL**: `http://localhost:8080`
- **인증**: JWT (Cookie 기반, `access_token` / `refresh_token`)
- **Rate Limiting**: Bucket4j 인메모리 (단일 서버)
- **에러 응답**: `ErrorBody` 통일 포맷
- **콘텐츠 타입**: `application/json` (SSE는 `text/event-stream`)

## 목차

1. [인증 (Auth)](#1-인증-auth) — 회원가입, 로그인, 토큰 갱신, 닉네임/프로필/탈퇴
2. [OAuth 2.0 소셜 로그인](#2-oauth-20-소셜-로그인) — 카카오, Google, 네이버
3. [데일리 게임](#3-데일리-게임-daily-game) — 오늘 문제, 추측 제출, 히스토리, 상태, 힌트
4. [랭킹](#4-랭킹-ranking) — 오늘 랭킹, SSE 스트림
5. [에러 응답 포맷](#5-에러-응답-포맷)
6. [인증 흐름](#6-인증-흐름) — JWT Cookie, Refresh Token Rotation
7. [CORS 설정](#7-cors-설정)
8. [Rate Limiting 상세](#8-rate-limiting-상세)
9. [공지](#9-공지-public)
10. [어드민 API](#10-어드민-api) — 문장, 사용자, 대시보드, 시스템

---

## 엔드포인트 요약

| Method | Path                                 | Auth   | Rate Limit    | 설명                 |
| ------ | ------------------------------------ | ------ | ------------- | -------------------- |
| GET    | `/health`                            | 불필요 | 없음          | 헬스체크             |
| POST   | `/auth/register`                     | 불필요 | AUTH 10/min   | 회원가입             |
| POST   | `/auth/login`                        | 불필요 | AUTH 10/min   | 로그인               |
| POST   | `/auth/refresh`                      | 불필요 | AUTH 10/min   | 토큰 갱신            |
| POST   | `/auth/logout`                       | 필수   | AUTH 10/min   | 로그아웃             |
| GET    | `/auth/me`                           | 필수   | READ 120/min   | 내 정보 조회         |
| PATCH  | `/auth/nickname`                     | 필수   | AUTH 10/min   | 닉네임 변경          |
| PATCH  | `/auth/profile-image`                | 필수   | AUTH 10/min   | 프로필 이미지 업로드 |
| DELETE | `/auth/profile-image`                | 필수   | AUTH 10/min   | 프로필 이미지 삭제   |
| DELETE | `/auth/account`                      | 필수   | AUTH 10/min   | 회원 탈퇴            |
| GET    | `/oauth2/authorization/{id}`         | 불필요 | 없음          | OAuth 로그인 시작    |
| GET    | `/daily/today`                       | 불필요 | READ 120/min   | 오늘 문제 조회       |
| POST   | `/daily/guess`                       | 불필요 | GUESS 40/min  | 추측 제출            |
| GET    | `/daily/history`                     | 필수   | READ 120/min   | 추측 히스토리        |
| GET    | `/daily/status`                      | 필수   | READ 120/min   | 게임 상태 조회       |
| GET    | `/daily/hints`                       | 필수   | READ 120/min   | 힌트 조회            |
| GET    | `/ranking/today`                     | 불필요 | READ 120/min   | 랭킹 조회            |
| GET    | `/ranking/stream`                    | 불필요 | SSE 10/min    | 랭킹 SSE 스트림      |
| GET    | `/announcements/active`              | 불필요 | READ 120/min   | 활성 공지 조회       |
| GET    | `/admin/sentences`                   | ADMIN  | ADMIN 120/min | 문장 목록            |
| POST   | `/admin/sentences`                   | ADMIN  | ADMIN 120/min | 문장 등록            |
| GET    | `/admin/sentences/{id}`              | ADMIN  | ADMIN 120/min | 문장 상세            |
| PATCH  | `/admin/sentences/{id}`              | ADMIN  | ADMIN 120/min | 문장 수정            |
| DELETE | `/admin/sentences/{id}`              | ADMIN  | ADMIN 120/min | 문장 삭제            |
| GET    | `/admin/sentences/{id}/stats`        | ADMIN  | ADMIN 120/min | 문장 통계            |
| GET    | `/admin/sentences/unused-count`      | ADMIN  | ADMIN 120/min | 미사용 문장 수       |
| POST   | `/admin/sentences/upload`            | ADMIN  | ADMIN 120/min | CSV 업로드           |
| POST   | `/admin/sentences/{id}/schedule`     | ADMIN  | ADMIN 120/min | 스케줄 지정          |
| DELETE | `/admin/sentences/{id}/schedule`     | ADMIN  | ADMIN 120/min | 스케줄 해제          |
| POST   | `/admin/sentences/similarity-test`   | ADMIN  | ADMIN 120/min | 유사도 테스트        |
| POST   | `/admin/sentences/duplicate-check`   | ADMIN  | ADMIN 120/min | 중복 검사            |
| POST   | `/admin/sentences/emergency-replace` | ADMIN  | ADMIN 120/min | 긴급 교체            |
| GET    | `/admin/users`                       | ADMIN  | ADMIN 120/min | 사용자 목록          |
| GET    | `/admin/users/{id}`                  | ADMIN  | ADMIN 120/min | 사용자 상세          |
| GET    | `/admin/users/{id}/history`          | ADMIN  | ADMIN 120/min | 게임 이력            |
| PATCH  | `/admin/users/{id}/role`             | ADMIN  | ADMIN 120/min | 역할 변경            |
| POST   | `/admin/users/{id}/ban`              | ADMIN  | ADMIN 120/min | 사용자 차단          |
| DELETE | `/admin/users/{id}/ban`              | ADMIN  | ADMIN 120/min | 차단 해제            |
| DELETE | `/admin/users/{id}`                  | ADMIN  | ADMIN 120/min | 강제 탈퇴            |
| PATCH  | `/admin/users/{id}/nickname`         | ADMIN  | ADMIN 120/min | 닉네임 강제 변경     |
| DELETE | `/admin/users/{id}/profile-image`    | ADMIN  | ADMIN 120/min | 프로필 이미지 삭제   |
| GET    | `/admin/dashboard/today`             | ADMIN  | ADMIN 120/min | 오늘 현황            |
| GET    | `/admin/dashboard/ranking`           | ADMIN  | ADMIN 120/min | 전체 랭킹            |
| GET    | `/admin/dashboard/stats/{date}`      | ADMIN  | ADMIN 120/min | 날짜별 통계          |
| GET    | `/admin/dashboard/trends`            | ADMIN  | ADMIN 120/min | 추이 데이터          |
| GET    | `/admin/dashboard/guess-log`         | ADMIN  | ADMIN 120/min | 추측 로그            |
| GET    | `/admin/system/announcements`        | ADMIN  | ADMIN 120/min | 공지 목록            |
| POST   | `/admin/system/announcements`        | ADMIN  | ADMIN 120/min | 공지 등록            |
| PATCH  | `/admin/system/announcements/{id}`   | ADMIN  | ADMIN 120/min | 공지 수정            |
| DELETE | `/admin/system/announcements/{id}`   | ADMIN  | ADMIN 120/min | 공지 삭제            |
| GET    | `/admin/system/status`               | ADMIN  | ADMIN 120/min | 시스템 상태          |
| POST   | `/admin/system/ranking-cache/reset`  | ADMIN  | ADMIN 120/min | 랭킹 캐시 리셋       |
| POST   | `/admin/system/rate-limit/reset`     | ADMIN  | ADMIN 120/min | Rate Limit 초기화    |
| GET    | `/admin/system/audit-logs`           | ADMIN  | ADMIN 120/min | 감사 로그            |

---

## 1. 인증 (Auth)

### `POST /auth/register` — 회원가입

- **Rate Limit**: AUTH (10/min, IP 기준)
- **요청**:

```json
{
  "username": "String (필수, 4~20자, 영문 시작, 영문/숫자/밑줄)",
  "password": "String (필수, 8~72자)"
}
```

- **응답**: `201 Created` + Set-Cookie (access_token, refresh_token)

```json
{
  "publicId": "UUID",
  "nickname": "String (자동 생성)",
  "profileUrl": null,
  "role": "ROLE_USER"
}
```

- **동작**: 로컬 계정 생성 + 닉네임 자동 생성 (NicknameGenerator) + JWT 쿠키 발급
- **에러**: `DUPLICATE_USERNAME`(409), `VALIDATION_FAILED`(400)

### `POST /auth/login` — 로그인

- **Rate Limit**: AUTH (10/min, IP 기준)
- **요청**:

```json
{
  "username": "String (필수)",
  "password": "String (필수)"
}
```

- **응답**: `200 OK` + Set-Cookie (access_token, refresh_token)

```json
{
  "publicId": "UUID",
  "nickname": "String",
  "profileUrl": "String | null",
  "role": "ROLE_USER | ROLE_ADMIN"
}
```

- **에러**: `INVALID_CREDENTIALS`(401)

### `POST /auth/refresh` — 토큰 갱신

- **Rate Limit**: AUTH (10/min, IP 기준)
- **요청**: Cookie `refresh_token` 필요
- **응답**: `200 OK` (본문 없음, Set-Cookie로 새 토큰 발급)
- **에러**: `INVALID_REFRESH_TOKEN`(401), `EXPIRED_TOKEN`(401), `REFRESH_TOKEN_REUSE_DETECTED`(401)

### `POST /auth/logout` — 로그아웃

- **인증**: 필수 (SecurityConfig `anyRequest().authenticated()`)
- **Rate Limit**: AUTH (10/min, IP 기준)
- **요청**: Cookie `access_token` (선택, 블랙리스트 등록용)
- **응답**: `200 OK` (본문 없음, 쿠키 삭제)
- **동작**: access token이 있으면 Redis 블랙리스트에 추가

### `GET /auth/me` — 내 정보 조회

- **인증**: 필수
- **Rate Limit**: READ (120/min, userId 기준)
- **응답**:

```json
{
  "publicId": "UUID",
  "nickname": "String",
  "profileUrl": "String",
  "role": "ROLE_USER | ROLE_ADMIN"
}
```

- **에러**: `MEMBER_NOT_FOUND`(404)

### `PATCH /auth/nickname` — 닉네임 변경

- **인증**: 필수
- **Rate Limit**: AUTH (10/min, IP 기준)
- **요청**:

```json
{
  "nickname": "String (필수, 2~12자, 한글/영문/숫자/밑줄/공백)"
}
```

- **응답**: `200 OK`

```json
{
  "publicId": "UUID",
  "nickname": "String",
  "profileUrl": "String",
  "role": "ROLE_USER | ROLE_ADMIN"
}
```

- **동작**: 닉네임 변경 + 오늘 랭킹 참여자인 경우 Redis Hash 닉네임 갱신 + SSE RANKING_UPDATE 브로드캐스트
- **에러**: `NICKNAME_DUPLICATED`(409), `NICKNAME_UNCHANGED`(400), `NICKNAME_FORBIDDEN`(400), `VALIDATION_FAILED`(400), `MEMBER_NOT_FOUND`(404)

### `PATCH /auth/profile-image` — 프로필 이미지 업로드

- **인증**: 필수
- **Rate Limit**: AUTH (10/min, IP 기준)
- **Content-Type**: `multipart/form-data`
- **요청**: `file` (필수, 이미지 파일, WebP, 최대 1MB)
- **응답**: `200 OK`

```json
{
  "publicId": "UUID",
  "nickname": "String",
  "profileUrl": "/uploads/profiles/{publicId}.webp?v={timestamp}",
  "role": "ROLE_USER | ROLE_ADMIN"
}
```

- **동작**: 파일 검증 (content-type + WebP magic bytes) → `{profileDir}/{publicId}.webp` 덮어쓰기 → DB profileUrl 갱신 → Redis Hash profileUrl 갱신 + SSE RANKING_UPDATE 브로드캐스트
- **에러**: `INVALID_FILE_TYPE`(400), `FILE_TOO_LARGE`(400), `FILE_UPLOAD_FAILED`(500), `MEMBER_NOT_FOUND`(404)

### `DELETE /auth/profile-image` — 프로필 이미지 삭제

- **인증**: 필수
- **Rate Limit**: AUTH (10/min, IP 기준)
- **응답**: `200 OK`

```json
{
  "publicId": "UUID",
  "nickname": "String",
  "profileUrl": null,
  "role": "ROLE_USER | ROLE_ADMIN"
}
```

- **동작**: DB profileUrl null 설정 → 파일 삭제 → Redis Hash profileUrl 갱신 + SSE RANKING_UPDATE 브로드캐스트
- **에러**: `MEMBER_NOT_FOUND`(404)

### `DELETE /auth/account` — 회원 탈퇴

- **인증**: 필수
- **Rate Limit**: AUTH (10/min)
- **응답**: `200 OK` (본문 없음, 쿠키 삭제)
- **동작**: 게임 기록 익명화, 인증 데이터 삭제, 프로필 이미지 파일 삭제, Redis 정리

---

## 2. OAuth 2.0 소셜 로그인

### `GET /oauth2/authorization/{registrationId}` — 로그인 시작

- `{registrationId}`: `kakao`, `google`, `naver`
- 프로바이더 인증 페이지로 리다이렉트

### OAuth 콜백 (Spring Security 자동 처리)

- 성공 시: `${OAUTH_REDIRECT_URI}` (기본 `http://localhost:3000`)로 리다이렉트 + 쿠키 발급
- 실패 시: 에러 메시지와 함께 리다이렉트

---

## 3. 데일리 게임 (Daily Game)

### `GET /daily/today` — 오늘 문제 조회

- **Rate Limit**: READ (120/min)
- **응답**:

```json
{
  "sentenceId": "UUID",
  "hintMask": "___ ___ ___",
  "wordCount": 3,
  "charCounts": [3, 3, 3],
  "expiresAt": "2026-03-23T15:00:00Z",
  "yesterdaySentence": "String | null",
  "yesterdayDate": "2026-03-22 | null"
}
```

- **에러**: `SENTENCE_NOT_FOUND`(404)

### `POST /daily/guess` — 추측 제출

- **Rate Limit**: GUESS (40/min)
- **요청**:

```json
{
  "sentenceId": "UUID (필수)",
  "guessText": "String (필수, 2~200자)"
}
```

- **응답 (로그인)**:

```json
{
  "similarity": 95.5,
  "attemptNumber": 1,
  "isCorrect": true,
  "gameStatus": "CLEARED",
  "timestamp": "2026-03-23T12:34:56.789Z"
}
```

- **응답 (익명)**: `attemptNumber`와 `gameStatus`가 **null**로 반환

```json
{
  "similarity": 95.5,
  "attemptNumber": null,
  "isCorrect": true,
  "gameStatus": null,
  "timestamp": "2026-03-23T12:34:56.789Z"
}
```

- **동작**:
  - 익명: 유사도만 반환 (GameSession/GuessHistory 미저장, attemptNumber/gameStatus null)
  - 로그인: GameSession 생성/갱신, GuessHistory 저장
  - similarity ≥ 95.0 → gameStatus `CLEARED`
  - CLEARED 후에도 추가 추측 가능 (attemptCount 동결, bestSimilarity만 갱신)
- **에러**: `VALIDATION_FAILED`(400), `INVALID_GUESS_TEXT`(400), `SENTENCE_NOT_FOUND`(404), `GAME_EXPIRED`(409), `CONCURRENT_MODIFICATION`(409), `AI_SERVICE_UNAVAILABLE`(503)

### `GET /daily/history` — 추측 히스토리

- **인증**: 필수
- **Rate Limit**: READ (120/min)
- **쿼리 파라미터**: `sentenceId` (필수, UUID)
- **응답 (세션 존재)**:

```json
{
  "guesses": [
    {
      "guessText": "String",
      "similarity": 85.5,
      "attemptNumber": 1,
      "createdAt": "2026-03-23T12:30:00Z"
    }
  ]
}
```

- **응답 (세션 미존재)**: 오늘 게임을 시작하지 않은 경우. 빈 guesses 배열 반환

```json
{
  "guesses": []
}
```

- **에러**: `SENTENCE_NOT_FOUND`(404)

### `GET /daily/status` — 게임 상태 조회

- **인증**: 필수
- **Rate Limit**: READ (120/min)
- **응답 (세션 존재)**:

```json
{
  "sentenceId": "UUID",
  "gameStatus": "IN_PROGRESS | CLEARED | EXPIRED",
  "bestSimilarity": 95.5,
  "attemptCount": 2,
  "clearedAt": "2026-03-23T12:35:00Z | null"
}
```

- **응답 (세션 미존재)**: 오늘 게임을 시작하지 않은 경우. `gameStatus`와 `clearedAt`이 null

```json
{
  "sentenceId": "UUID",
  "gameStatus": null,
  "bestSimilarity": 0,
  "attemptCount": 0,
  "clearedAt": null
}
```

- **에러**: `SENTENCE_NOT_FOUND`(404)

### `GET /daily/hints` — 힌트 조회

- **인증**: 필수
- **Rate Limit**: READ (120/min)
- **쿼리 파라미터**: `sentenceId` (필수, UUID)
- **응답**:

```json
{
  "hints": [
    {
      "guessText": "String",
      "similarity": 85.5
    }
  ]
}
```

- **동작**:
  - 요청자의 bestSimilarity가 60% 미만이면 403 반환
  - 다른 유저의 추측 중 요청자의 bestSimilarity 미만만 반환
  - 동일 guessText는 최고 유사도 1건만 (DISTINCT ON)
  - 요청자 본인의 추측은 제외
  - 최대 5개, 유사도 높은 순 정렬
- **에러**: `HINT_NOT_AVAILABLE`(403), `SESSION_NOT_FOUND`(404), `SENTENCE_NOT_FOUND`(404), `MEMBER_NOT_FOUND`(404)

---

## 4. 랭킹 (Ranking)

### `GET /ranking/today` — 오늘 랭킹 조회

- **Rate Limit**: READ (120/min)
- **인증**: 선택 (인증 시 추가 필드 반환)
- **응답 (비인증)**:

```json
{
  "rankings": [
    {
      "rank": 1,
      "publicId": "UUID",
      "nickname": "String",
      "profileUrl": "String | null",
      "similarity": 100.0,
      "attemptCount": 1
    }
  ],
  "totalPlayers": 42,
  "myRank": null,
  "yesterdayRank": null,
  "yesterdayTotalPlayers": null
}
```

- **응답 (인증)**:

```json
{
  "rankings": [...],
  "totalPlayers": 42,
  "myRank": {
    "rank": 15,
    "similarity": 85.5,
    "attemptCount": 3
  },
  "yesterdayRank": 5,
  "yesterdayTotalPlayers": 38
}
```

- 상위 10명 고정, 페이지네이션 없음
- `myRank`: 오늘 게임 미참여 시 null (Redis ZREVRANK 기반)
- `yesterdayRank`: 어제 게임 미참여 시 null (game_sessions.final_rank)
- `yesterdayTotalPlayers`: 어제 데이터 없으면 null (daily_sentences.total_players)

### `GET /ranking/stream` — 랭킹 SSE 스트림

- **Rate Limit**: SSE (10/min, 연결 시도 기준)
- **Content-Type**: `text/event-stream`
- **최대 동시 연결**: 500
- **이벤트 종류**:

| 이벤트           | 설명                               | 데이터                                      |
| ---------------- | ---------------------------------- | ------------------------------------------- |
| `RANKING_UPDATE` | 랭킹 변경 (연결 시 즉시 + 변경 시) | `{"rankings":[...],"totalPlayers":N}`       |
| `DAY_CHANGE`     | 자정 날짜 전환                     | `{"sentenceId":"...","hintMask":"...",...}` |
| `ANNOUNCEMENT`   | 공지 생성/수정/삭제                | `{"id":N,"title":"...","type":"INFO"}`      |
| `HEARTBEAT`      | 연결 유지 (10초 간격)              | `{"sseConnectionCount":N}`                  |

- 100ms 디바운스 (짧은 시간 내 다수 변경 시 한 번만 전송)
- **에러**: `SSE_MAX_CONNECTIONS`(503)

---

## 5. 에러 응답 포맷

모든 에러는 `ErrorBody` 통일 포맷:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "guessText: 2~200자 사이여야 합니다",
  "timestamp": "2026-03-23T12:34:56.789Z"
}
```

### 에러 코드 목록

| 코드                           | HTTP | 설명                                     |
| ------------------------------ | ---- | ---------------------------------------- |
| `UNAUTHORIZED`                 | 401  | 인증이 필요한 엔드포인트에 미인증        |
| `INVALID_TOKEN`                | 401  | JWT 유효하지 않음                        |
| `EXPIRED_TOKEN`                | 401  | JWT 만료                                 |
| `BLACKLISTED_TOKEN`            | 401  | 로그아웃된 토큰                          |
| `REFRESH_TOKEN_REUSE_DETECTED` | 401  | Refresh Token 재사용 감지                |
| `INVALID_REFRESH_TOKEN`        | 401  | Refresh Token 유효하지 않음              |
| `MEMBER_NOT_FOUND`             | 404  | 회원 미존재                              |
| `NICKNAME_DUPLICATED`          | 409  | 이미 사용 중인 닉네임                    |
| `NICKNAME_UNCHANGED`           | 400  | 현재 닉네임과 동일                       |
| `NICKNAME_FORBIDDEN`           | 400  | 사용할 수 없는 닉네임 (금칙어)           |
| `DUPLICATE_USERNAME`           | 409  | 이미 사용 중인 아이디                    |
| `INVALID_CREDENTIALS`          | 401  | 아이디 또는 비밀번호 불일치              |
| `INVALID_FILE_TYPE`            | 400  | 지원하지 않는 파일 형식                  |
| `FILE_TOO_LARGE`               | 413  | 파일 크기 제한 초과                      |
| `FILE_UPLOAD_FAILED`           | 500  | 파일 업로드 실패                         |
| `OAUTH_PROVIDER_NOT_SUPPORTED` | 400  | 지원하지 않는 OAuth 프로바이더           |
| `VALIDATION_FAILED`            | 400  | 요청 검증 실패                           |
| `MISSING_PARAMETER`            | 400  | 필수 파라미터 누락                       |
| `METHOD_NOT_ALLOWED`           | 405  | HTTP 메서드 미지원                       |
| `INVALID_GUESS_TEXT`           | 400  | 정규화 후 빈 문자열                      |
| `HINT_NOT_AVAILABLE`           | 403  | 힌트 조건 미달 (bestSimilarity 60% 미만) |
| `SENTENCE_NOT_FOUND`           | 404  | 오늘 문제 없음                           |
| `SESSION_NOT_FOUND`            | 404  | 게임 세션 없음                           |
| `GAME_EXPIRED`                 | 409  | 게임 세션 만료                           |
| `CONCURRENT_MODIFICATION`      | 409  | 낙관적 락 충돌                           |
| `AI_SERVICE_UNAVAILABLE`       | 503  | AI 서비스 불가                           |
| `RATE_LIMIT_EXCEEDED`          | 429  | 요청 초과 (Retry-After 헤더 포함)        |
| `SSE_MAX_CONNECTIONS`          | 503  | SSE 최대 연결 초과                       |
| `MEMBER_BANNED`                | 403  | 차단된 계정                              |
| `SENTENCE_DUPLICATE`           | 409  | 이미 존재하는 문장                       |
| `SENTENCE_ALREADY_USED`        | 409  | 이미 출제된 문장                         |
| `CSV_PARSE_ERROR`              | 400  | CSV 파싱 오류                            |
| `ANNOUNCEMENT_NOT_FOUND`       | 404  | 공지 미존재                              |
| `ADMIN_SELF_ACTION`            | 400  | 본인에 대한 어드민 액션                  |
| `ROLE_ALREADY_ASSIGNED`        | 400  | 이미 동일한 역할                         |
| `INTERNAL_SERVER_ERROR`        | 500  | 내부 서버 오류                           |

---

## 6. 인증 흐름

### JWT Cookie 기반

1. 소셜 로그인 → `access_token`, `refresh_token` 쿠키 발급
2. 이후 요청: 브라우저가 자동으로 쿠키 전송
3. Access Token 만료 시: `POST /auth/refresh` → 새 토큰 발급
4. Refresh Token Rotation: 갱신 시마다 새 Refresh Token 발급

### 쿠키 설정

| 항목              | 값                          |
| ----------------- | --------------------------- |
| HttpOnly          | `true` (JS 접근 불가)       |
| Secure            | 개발: `false`, 운영: `true` |
| Max-Age (Access)  | 기본 30분                   |
| Max-Age (Refresh) | 기본 7일                    |

---

## 7. CORS 설정

| 항목            | 값                                                     |
| --------------- | ------------------------------------------------------ |
| Allowed Origins | `${OAUTH_REDIRECT_URI}` (기본 `http://localhost:3000`) |
| Allowed Methods | GET, POST, PUT, PATCH, DELETE, OPTIONS                 |
| Allowed Headers | \*                                                     |
| Credentials     | `true` (쿠키 포함)                                     |

---

## 8. Rate Limiting 상세

| 그룹  | 대상 엔드포인트                | 제한    | 식별 기준                |
| ----- | ------------------------------ | ------- | ------------------------ |
| GUESS | `POST /daily/guess`            | 40/min  | 익명: IP, 로그인: userId |
| READ  | 모든 `GET` (OAuth/health 제외) | 120/min | 익명: IP, 로그인: userId |
| SSE   | `GET /ranking/stream`          | 10/min  | 익명: IP, 로그인: userId |
| AUTH  | `/auth/**`                     | 10/min  | 항상 IP                  |
| ADMIN | `/admin/**`                    | 120/min | userId 기준              |

**응답 헤더**:

- 성공 시: `X-Rate-Limit-Remaining: <남은 토큰>`
- 429 시: `Retry-After: <대기 초>`

---

## 9. 공지 (Public)

### `GET /announcements/active` — 현재 활성 공지 조회

- **Rate Limit**: READ (120/min)
- **응답**:

```json
[
  {
    "id": 1,
    "title": "서버 점검 안내",
    "content": "내용 (nullable)",
    "type": "INFO | MAINTENANCE | WARNING",
    "startsAt": "2026-03-30T00:00:00Z",
    "endsAt": "2026-03-31T00:00:00Z | null"
  }
]
```

---

## 10. 어드민 API

모든 어드민 API는 `hasRole('ADMIN')` 인증 필수. Rate Limit: ADMIN (120/min, userId 기준).

### 10.1 문장 관리

#### `GET /admin/sentences` — 문장 목록

- **쿼리**: `status` (선택, ACTIVE/DISABLED), `page` (기본 0), `size` (기본 20)
- **응답**: `{sentences: [...], page, size, totalElements, totalPages}`

#### `POST /admin/sentences` — 문장 등록

- **요청**: `{"sentence": "String (필수, 최대 500자)"}`
- **응답**: `201 Created` + SentenceResponse

#### `GET /admin/sentences/{publicId}` — 문장 상세

#### `PATCH /admin/sentences/{publicId}` — 문장 수정

- **요청**: `{"sentence": "String (필수, 최대 500자)"}`

#### `DELETE /admin/sentences/{publicId}` — 문장 삭제

- 미출제 문장만 삭제 가능

#### `GET /admin/sentences/{publicId}/stats` — 문장별 통계

- **응답**: `{totalSessions, clearedSessions, clearRate, avgSimilarity, avgAttemptCount}`

#### `GET /admin/sentences/unused-count` — 미사용 문장 수

- **응답**: `{"count": N}`

#### `POST /admin/sentences/upload` — CSV 업로드

- **Content-Type**: `multipart/form-data` (`file` 파라미터)
- **응답**: `201 Created` + `{totalRows, successCount, duplicateCount}`

#### `POST /admin/sentences/{publicId}/schedule` — 스케줄 지정

- **요청**: `{"date": "2026-04-01"}`

#### `DELETE /admin/sentences/{publicId}/schedule` — 스케줄 해제

#### `POST /admin/sentences/similarity-test` — 유사도 테스트

- **요청**: `{"sentence": "정답", "guessText": "추측"}`
- **응답**: `{sentence, guessText, similarity}`

#### `POST /admin/sentences/duplicate-check` — 중복 검사

- **요청**: `{"sentence": "문장"}`
- **응답**: `{hasDuplicate, similarEntries: [{sentence, similarity}]}`

#### `POST /admin/sentences/emergency-replace` — 긴급 교체

- **요청**: `{"newSentencePublicId": "UUID", "returnOldToPool": true}`
- **동작**: 기존 세션+추측 기록 삭제, Redis 랭킹 초기화, DAY_CHANGE SSE 브로드캐스트

### 10.2 사용자 관리

#### `GET /admin/users` — 사용자 목록

- **쿼리**: `nickname` (선택), `banned` (선택, true/false), `page`, `size`
- **응답**: `{users: [{publicId, nickname, profileUrl, role, banned, bannedAt, provider, email, createdAt}], page, size, totalElements, totalPages}`

#### `GET /admin/users/{publicId}` — 사용자 상세

- **응답**: 사용자 정보 + `activity: {totalParticipations, totalClears, avgAttemptCount, bestRank}`

#### `GET /admin/users/{publicId}/history` — 게임 이력

- **응답**: `{history: [{date, sentence, gameStatus, bestSimilarity, attemptCount, finalRank, clearedAt}]}`

#### `PATCH /admin/users/{publicId}/role` — 역할 변경

- **요청**: `{"role": "ADMIN | USER"}`

#### `POST /admin/users/{publicId}/ban` — 사용자 차단

- **동작**: banned 플래그 + refresh token revoke + access token 블랙리스트

#### `DELETE /admin/users/{publicId}/ban` — 차단 해제

#### `DELETE /admin/users/{publicId}` — 강제 탈퇴

#### `PATCH /admin/users/{publicId}/nickname` — 닉네임 강제 변경

- **요청**: `{"nickname": "String (최대 12자)"}`

#### `DELETE /admin/users/{publicId}/profile-image` — 프로필 이미지 삭제

### 10.3 대시보드

#### `GET /admin/dashboard/today` — 오늘 현황

- **응답**: `{sentenceId, sentence, totalParticipants, clearedCount, inProgressCount, avgSimilarity, avgAttemptCount, unusedSentenceCount, sseConnectionCount}`

#### `GET /admin/dashboard/ranking` — 전체 랭킹

- **쿼리**: `date` (선택, 기본 오늘)
- **응답**: `{rankings: [{rank, publicId, nickname, profileUrl, similarity, attemptCount}], totalPlayers}`

#### `GET /admin/dashboard/stats/{date}` — 날짜별 통계

- **응답**: `{date, sentence, totalParticipants, clearedCount, clearRate, avgSimilarity, avgAttemptCount}`

#### `GET /admin/dashboard/trends` — 추이 데이터

- **쿼리**: `days` (기본 30)
- **응답**: `{trends: [{date, participants, clears, clearRate, newMembers}]}`

#### `GET /admin/dashboard/guess-log` — 추측 로그

- **쿼리**: `date` (필수), `memberPublicId` (선택)
- **응답**: `{logs: [{memberPublicId, nickname, guessText, similarity, attemptNumber, createdAt}]}`

### 10.4 시스템 관리

#### `GET /admin/system/announcements` — 공지 목록

#### `POST /admin/system/announcements` — 공지 등록

- **요청**: `{"title": "String", "content": "String?", "type": "INFO|MAINTENANCE|WARNING", "startsAt": "ISO", "endsAt": "ISO?"}`
- **응답**: `201 Created` + AnnouncementResponse

#### `PATCH /admin/system/announcements/{id}` — 공지 수정

#### `DELETE /admin/system/announcements/{id}` — 공지 삭제

#### `GET /admin/system/status` — 시스템 상태

- **응답**: `{sseConnectionCount, aiServiceHealthy, aiServiceResponseMs, redisHealthy, totalMembers, totalSentences, unusedSentences}`

#### `POST /admin/system/ranking-cache/reset` — 랭킹 캐시 리셋

#### `POST /admin/system/rate-limit/reset` — Rate Limit 초기화

#### `GET /admin/system/audit-logs` — 감사 로그

- **쿼리**: `action` (선택), `dateFrom`/`dateTo` (선택, ISO DateTime), `page`, `size`
- **응답**: Spring Data `Page<AuditLogResponse>` — `{content: [{id, adminNickname, action, targetType, targetId, detail, ipAddress, createdAt}], totalElements, totalPages, number, size}`
