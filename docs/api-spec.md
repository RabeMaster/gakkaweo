# Backend REST API 명세

## 개요

- **Base URL**: `http://localhost:8080`
- **인증**: JWT (Cookie 기반, `access_token` / `refresh_token`)
- **Rate Limiting**: Bucket4j 인메모리 (단일 서버)
- **에러 응답**: `ErrorBody` 통일 포맷
- **콘텐츠 타입**: `application/json` (SSE는 `text/event-stream`)

---

## 엔드포인트 요약

| Method | Path                         | Auth   | Rate Limit   | 설명              |
| ------ | ---------------------------- | ------ | ------------ | ----------------- |
| GET    | `/health`                    | 불필요 | 없음         | 헬스체크          |
| POST   | `/auth/register`             | 불필요 | AUTH 10/min  | 회원가입          |
| POST   | `/auth/login`                | 불필요 | AUTH 10/min  | 로그인            |
| POST   | `/auth/refresh`              | 불필요 | AUTH 10/min  | 토큰 갱신         |
| POST   | `/auth/logout`               | 필수   | AUTH 10/min  | 로그아웃          |
| GET    | `/auth/me`                   | 필수   | READ 60/min  | 내 정보 조회      |
| PATCH  | `/auth/nickname`             | 필수   | AUTH 10/min  | 닉네임 변경       |
| DELETE | `/auth/account`              | 필수   | AUTH 10/min  | 회원 탈퇴         |
| GET    | `/oauth2/authorization/{id}` | 불필요 | 없음         | OAuth 로그인 시작 |
| GET    | `/daily/today`               | 불필요 | READ 60/min  | 오늘 문제 조회    |
| POST   | `/daily/guess`               | 불필요 | GUESS 40/min | 추측 제출         |
| GET    | `/daily/history`             | 필수   | READ 60/min  | 추측 히스토리     |
| GET    | `/daily/status`              | 필수   | READ 60/min  | 게임 상태 조회    |
| GET    | `/ranking/today`             | 불필요 | READ 60/min  | 랭킹 조회         |
| GET    | `/ranking/stream`            | 불필요 | SSE 10/min   | 랭킹 SSE 스트림   |

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
- **Rate Limit**: READ (60/min, userId 기준)
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

### `DELETE /auth/account` — 회원 탈퇴

- **인증**: 필수
- **Rate Limit**: AUTH (10/min)
- **응답**: `200 OK` (본문 없음, 쿠키 삭제)
- **동작**: 게임 기록 익명화, 인증 데이터 삭제, Redis 정리

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

- **Rate Limit**: READ (60/min)
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
- **Rate Limit**: READ (60/min)
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
- **Rate Limit**: READ (60/min)
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

---

## 4. 랭킹 (Ranking)

### `GET /ranking/today` — 오늘 랭킹 조회

- **Rate Limit**: READ (60/min)
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
| `HEARTBEAT`      | 연결 유지 (10초 간격)              | (빈 데이터)                                 |

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

| 코드                           | HTTP | 설명                              |
| ------------------------------ | ---- | --------------------------------- |
| `UNAUTHORIZED`                 | 401  | 인증이 필요한 엔드포인트에 미인증 |
| `INVALID_TOKEN`                | 401  | JWT 유효하지 않음                 |
| `EXPIRED_TOKEN`                | 401  | JWT 만료                          |
| `BLACKLISTED_TOKEN`            | 401  | 로그아웃된 토큰                   |
| `REFRESH_TOKEN_REUSE_DETECTED` | 401  | Refresh Token 재사용 감지         |
| `INVALID_REFRESH_TOKEN`        | 401  | Refresh Token 유효하지 않음       |
| `MEMBER_NOT_FOUND`             | 404  | 회원 미존재                       |
| `NICKNAME_DUPLICATED`          | 409  | 이미 사용 중인 닉네임             |
| `NICKNAME_UNCHANGED`           | 400  | 현재 닉네임과 동일                |
| `NICKNAME_FORBIDDEN`           | 400  | 사용할 수 없는 닉네임 (금칙어)    |
| `DUPLICATE_USERNAME`           | 409  | 이미 사용 중인 아이디             |
| `INVALID_CREDENTIALS`          | 401  | 아이디 또는 비밀번호 불일치       |
| `OAUTH_PROVIDER_NOT_SUPPORTED` | 400  | 지원하지 않는 OAuth 프로바이더    |
| `VALIDATION_FAILED`            | 400  | 요청 검증 실패                    |
| `MISSING_PARAMETER`            | 400  | 필수 파라미터 누락                |
| `METHOD_NOT_ALLOWED`           | 405  | HTTP 메서드 미지원                |
| `INVALID_GUESS_TEXT`           | 400  | 정규화 후 빈 문자열               |
| `SENTENCE_NOT_FOUND`           | 404  | 오늘 문제 없음                    |
| `SESSION_NOT_FOUND`            | 404  | 게임 세션 없음                    |
| `GAME_EXPIRED`                 | 409  | 게임 세션 만료                    |
| `CONCURRENT_MODIFICATION`      | 409  | 낙관적 락 충돌                    |
| `AI_SERVICE_UNAVAILABLE`       | 503  | AI 서비스 불가                    |
| `RATE_LIMIT_EXCEEDED`          | 429  | 요청 초과 (Retry-After 헤더 포함) |
| `SSE_MAX_CONNECTIONS`          | 503  | SSE 최대 연결 초과                |
| `INTERNAL_SERVER_ERROR`        | 500  | 내부 서버 오류                    |

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
| Allowed Methods | GET, POST, PUT, PATCH, DELETE, OPTIONS                  |
| Allowed Headers | \*                                                     |
| Credentials     | `true` (쿠키 포함)                                     |

---

## 8. Rate Limiting 상세

| 그룹  | 대상 엔드포인트                | 제한   | 식별 기준                |
| ----- | ------------------------------ | ------ | ------------------------ |
| GUESS | `POST /daily/guess`            | 40/min | 익명: IP, 로그인: userId |
| READ  | 모든 `GET` (OAuth/health 제외) | 60/min | 익명: IP, 로그인: userId |
| SSE   | `GET /ranking/stream`          | 10/min | 익명: IP, 로그인: userId |
| AUTH  | `/auth/**`                     | 10/min | 항상 IP                  |

**응답 헤더**:

- 성공 시: `X-Rate-Limit-Remaining: <남은 토큰>`
- 429 시: `Retry-After: <대기 초>`
