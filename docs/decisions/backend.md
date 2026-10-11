# 백엔드 의사결정

## 패키지 구조

도메인 계층과 기능 계층을 분리하는 하이브리드 구조를 채택했다.

```
backend/src/main/java/.../backend/
├── domain/          # 순수 도메인 - 엔티티, 리포지토리, 비즈니스 규칙
│   ├── member/      #   Member, SocialAccount, LocalAccount
│   ├── game/        #   DailySentence, GameSession, GuessHistory
│   ├── auth/        #   RefreshToken, AuthSession
│   ├── admin/       #   Announcement, AuditLog, SentenceUpload
│   └── common/      #   BaseTimeEntity, BaseAuditableEntity
├── auth/            # 기능: 인증 - 서비스, 컨트롤러, JWT, OAuth2
├── game/            # 기능: 게임 - 서비스, 컨트롤러, DTO
├── ranking/         # 기능: 랭킹 - Redis 기반 서비스, SSE 이벤트
├── member/          # 기능: 회원 - 프로필 이미지, Redis 동기화
├── admin/           # 기능: 어드민 - 관리 서비스, 컨트롤러
├── announcement/    # 기능: 공지 - 활성 공지 조회
├── ratelimit/       # 기능: Rate Limiting - 필터, 버킷 관리
├── healthcheck/     # 헬스체크 엔드포인트
├── infra/           # 외부 의존성 - AI Service 클라이언트, Circuit Breaker
├── common/          # 공통 - 예외, Redis 키 상수, 시간, 유틸
└── config/          # 글로벌 설정 - SecurityConfig 등
```

### 왜 이 구조인가

- **domain/**: 엔티티는 JPA 매핑과 비즈니스 규칙만 담는다. `@Transactional`이나 외부 서비스 호출 로직이 들어가지 않는다. `GameSession.markCleared()`, `GameSession.updateBestSimilarity()` 같은 도메인 메서드로 상태 변경을 캡슐화한다.
- **기능 패키지** (auth/, game/, ranking/): 도메인 객체를 조합하고 트랜잭션을 관리하는 서비스 계층이다. 각 기능은 자기 컨트롤러, 서비스, DTO를 갖는다.
- **infra/**: AI 서비스 HTTP 통신, Circuit Breaker 같은 외부 의존성을 격리한다. 기능 서비스에서는 `SimilarityService` 인터페이스만 의존한다.

### DTO 패턴

`XxxResponse.from(Entity)` 정적 팩토리 메서드로 엔티티 → DTO 변환을 캡슐화한다. 컨트롤러에서 엔티티를 직접 반환하지 않는다.

```java
// 예시
public record MemberResponse(UUID publicId, String nickname, ...) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getPublicId(), member.getNickname(), ...);
    }
}
```

## 트랜잭션 전략

### 원칙: TX 내 I/O 분리

트랜잭션 안에서는 DB 쓰기만 수행하고, BCrypt 해싱, Redis 연산, HTTP 호출 같은 I/O는 트랜잭션 밖에서 처리하는 것을 원칙으로 한다.

| 연산           | TX 안/밖  | 이유                                                               |
| -------------- | --------- | ------------------------------------------------------------------ |
| DB 읽기/쓰기   | 안        | 데이터 정합성과 원자성 보장                                        |
| BCrypt 해싱    | 밖        | CPU-bound 연산, 트랜잭션 롤백 대상 아님                            |
| 랭킹 Redis 연산 | 밖        | 실패해도 게임 진행은 롤백하지 않음 |
| AI 서비스 호출 | 밖        | 점수를 계산한 뒤 DB 기록을 트랜잭션으로 저장                       |

### AI 서비스 호출

`DailyGameService.guessAuthenticated()`는 AI 유사도를 먼저 계산하고, `TransactionTemplate` 안에서 게임 상태와 추측 기록을 저장한다. AI 호출 중에는 DB 쓰기 트랜잭션을 유지하지 않는다.

### 랭킹 Redis 업데이트

`RankingService.updateRanking()`은 `@Transactional`이 없다. Redis 업데이트가 실패해도 게임 진행은 롤백하지 않는다. 불일치가 발생하면 어드민 패널에서 랭킹 캐시를 리셋할 수 있다.

### 어드민 긴급 교체

`AdminSentenceService.emergencyReplace()`는 `TransactionTemplate`을 사용하여 수동으로 트랜잭션을 관리한다. 기존 세션/추측 기록 삭제 → 새 문장 배정을 하나의 TX로 묶고, Redis 정리와 SSE 브로드캐스트는 TX 밖에서 수행한다.

## 인증

### JWT Cookie 기반 인증

액세스 토큰은 JWT로 발급하고 `HttpOnly` 쿠키로 보낸다. 서명·만료 시간을 검사한 뒤 로그인별 세션 ID(`sid`)로 DB의 세션과 회원 상태를 확인한다. 인증 상태는 DB에서만 관리하며 Redis나 백엔드 메모리에 복제하지 않는다.

- **액세스 토큰**: API 요청에 사용한다. DB의 세션이 유효하고 회원의 차단·권한 상태가 맞아야 인증된다.
- **갱신 토큰**: 액세스 토큰을 다시 발급할 때 사용한다. DB에는 원문 대신 SHA-256 해시를 저장한다.
- **표시 쿠키**: `has_session`은 화면에서 로그인 확인 요청을 줄이는 용도다. 이 쿠키만으로 인증하지 않는다.

쿠키에는 `HttpOnly`, HTTPS의 `Secure`, `SameSite` 설정을 적용한다. 갱신 토큰의 Path는 `/auth`이며 로그아웃에도 전달된다. 이름이 같은 쿠키의 충돌을 막기 위해 이전 경로의 쿠키도 발급·삭제 응답에서 지운다.

### 로그인 세션과 로그아웃

한 번 로그인해서 발급한 토큰들은 하나의 세션 ID를 공유한다. DB의 `auth_sessions`에는 소유 회원, 생성일, 만료 시간, 폐기 여부를 저장한다. 요청 인증은 필요한 세션·회원 정보만 SQL 한 번으로 조회한다.

회원과 로그인 세션은 DB가 부여한 숫자 `id`와 외부에 사용하는 UUID `publicId`를 분리한다. JWT의 회원 식별자와 `sid`에는 공개 UUID를 넣고, 회원 조회 후 내부 계정 조회·잠금·세션 폐기·만료 정리는 숫자 ID를 사용한다. 갱신 토큰의 `session_id`도 숫자 세션 ID를 참조한다. 기존 `family_id`는 이전 토큰과의 형식을 유지하지만 내부 세션 조회와 폐기에는 사용하지 않는다.

- **현재 기기 로그아웃**: 해당 로그인 세션과 갱신 토큰만 폐기한다. 다른 로그인은 유지한다.
- **모든 기기 로그아웃**: 회원의 모든 로그인 세션과 갱신 토큰을 폐기한다.
- **차단·권한 변경·비밀번호 변경·탈퇴**: 기존 로그인을 모두 폐기한다. 차단을 해제해도 이전 로그인이 되살아나지 않는다.

`AuthSessionService`가 인증 확인과 발급·갱신·폐기를 담당한다. 발급·갱신·폐기는 회원 행을 먼저 잠그고 같은 DB 트랜잭션에서 처리한다. 갱신 토큰 재사용을 발견하면 해당 세션을 폐기하고 DB 저장이 끝난 뒤 오류를 반환한다.

만료 정리도 같은 회원 행을 먼저 잠근다. 만료된 세션 또는 갱신 토큰이 있는 회원을 조회한 뒤, 회원별 별도 DB 트랜잭션에서 만료 조건을 다시 확인하여 해당 회원의 데이터만 삭제한다. 대상 조회 뒤 갱신된 세션은 보존하고 이미 탈퇴한 회원은 건너뛴다. 정리 중 오류가 발생하면 해당 회원의 세션·토큰 삭제가 함께 취소되며, 앞서 정리를 마친 다른 회원의 결과는 유지된다.

회원가입의 계정 저장과 세션 발급, 비밀번호·차단·권한 변경과 세션 폐기를 같은 DB 트랜잭션으로 묶는다. 저장에 실패하면 함께 취소된다. 서버 재시작이나 Redis 데이터 유실·복원은 로그인 유효성에 영향을 주지 않는다. DB 인증 조회에 실패하면 503으로 거부한다. 이미 인증을 마치고 실행 중인 요청까지 취소하지는 않는다.

로컬·소셜 로그인 성공 지표는 세션과 토큰 발급이 정상적으로 끝난 뒤 기록한다.

인증 요청마다 DB 조회 비용이 생기지만 두 저장소의 인증 상태를 동기화하는 복잡성을 줄이는 선택이다. Redis는 랭킹·요청 제한 등 기존 용도로 사용한다. V18 적용 시 이전 갱신 토큰은 모두 폐기하고 세션 ID 없는 이전 액세스 토큰도 거부하므로 기존 사용자는 다시 로그인해야 한다.

### 로컬 계정 비밀번호 변경

`canChangePassword`는 아이디·비밀번호로 가입한 계정이 실제로 존재하는지 나타낸다. 소셜 로그인만 사용하는 계정에는 비밀번호 변경 버튼을 보여 주지 않으며, 서버도 변경 요청을 거부한다.

현재 비밀번호를 확인한 뒤 새 비밀번호를 저장하고 모든 기기를 로그아웃한다. 비밀번호는 8자 이상·UTF-8 72바이트 이하로 제한한다. 시간이 걸리는 BCrypt 연산은 쓰기 트랜잭션 밖에서 수행하고, 회원 잠금 후 비밀번호가 중간에 바뀌지 않았는지 다시 확인한다.

회원의 `entityVersion`은 JPA의 동시 수정 충돌 확인에만 사용한다. 오래된 프로필 저장이 차단·권한 변경을 덮어쓰지 않도록 하며, JWT 인증이나 로그아웃 판정에는 쓰지 않는다.

## 권한 모델

`USER` < `ADMIN` < `SUPERADMIN`. Spring Security `RoleHierarchy`로 자동 포괄. API 응답 `role`은 prefix 없이 노출하고 내부에서만 `ROLE_`을 쓴다.

### 가드 3층

| 층           | 위치                                                           | 거부 코드           |
| ------------ | -------------------------------------------------------------- | ------------------- |
| Path matcher | `SecurityConfig` (`/admin/**` = ADMIN, 위험 액션 = SUPERADMIN) | `ACCESS_DENIED`     |
| 대상 보호    | `AdminUserService.requireSufficientRoleOver`                   | `INSUFFICIENT_ROLE` |
| 부여 정책    | `AdminUserService.requireRoleAssignmentAllowed`                | `INSUFFICIENT_ROLE` |

> 거부 사유가 다르므로 응답 코드를 분리해 클라이언트가 구분할 수 있게 했다.

### 액션별 매트릭스

| 액션                                         | Path         | 대상 보호 | 부여 정책           |
| -------------------------------------------- | ------------ | --------- | ------------------- |
| 역할 변경                                    | `ADMIN`      | ✓         | ✓ (SUPERADMIN-only) |
| 차단/해제, 닉네임 강제, 프로필 강제 삭제     | `ADMIN`      | ✓         | -                   |
| 강제 탈퇴                                    | `SUPERADMIN` | ✓         | -                   |
| 긴급 교체, 랭킹 캐시 리셋, Rate Limit 초기화 | `SUPERADMIN` | -         | -                   |

`ADMIN`은 가역적이고 단일 사용자에 한정된 액션, `SUPERADMIN`은 비가역(강제 탈퇴, 긴급 교체, 캐시 리셋), 시스템 광역(Rate Limit), 권한 부여 메타-액션(역할 변경)을 수행한다.

> 역할 변경은 가역적이지만 다른 액션의 전제 조건이 되는 권한 부여이므로 SUPERADMIN으로 분리했다.

### SUPERADMIN 부여 채널

`SUPERADMIN`은 운영 SQL로만 부여한다. API는 DTO `@Pattern("USER|ADMIN")` 1차 + 서비스 `requireRoleAssignmentAllowed` 2차로 차단하고, 시딩은 최초 admin 한 건만 만든다. `USER → ADMIN` 승격도 SUPERADMIN 행위자만 가능하다.

> 부여 채널을 운영 SQL로 좁히면 단일 ADMIN 탈취가 권한 상승으로 이어지지 않는다. ADMIN끼리의 상호 임명도 권한 확산 통제가 어려워 같은 정책으로 묶었다.

## 랭킹 시스템

### Redis Sorted Set 스코어 인코딩

단일 `double` 값에 유사도, 시도 횟수, 시간을 인코딩한다.

```java
// 100% 달성자: 선착순 (자정 KST 기준 경과 밀리초)
score = (similarity * 10 * 1_000_000_000) - clearedAtMillis

// 95~99.9%: 유사도 > 시도 횟수 > 경과 시간
score = (similarity * 10 * 1_000_000_000) - (min(attemptCount, 999) * 100_000) - elapsedSeconds
```

- `reverseRank`로 점수가 높을수록 상위 랭크
- 100% 달성자는 `clearedAt`이 빠를수록 점수가 높음 (빼기이므로). 차감값은 에포크 초가 아니라 자정(KST) 기준 경과 밀리초를 쓴다. 에포크 값은 자릿수가 커서 double 가수부 정밀도 한계에 걸릴 수 있는데, 자정 기준으로 바꾸면 값의 크기가 작아져 밀리초 단위 선착순까지 정확하게 구분된다
- 95~99.9%에서는 유사도가 1차, 시도 횟수가 2차, 경과 시간이 3차 정렬 기준
- 시도 횟수는 스코어 계산 시 999 상한(`ATTEMPT_COUNT_SCORE_CAP`)을 둔다. 상한이 없으면 비정상적으로 큰 시도 횟수가 유사도 성분까지 침범할 수 있기 때문이다. 상한은 스코어 계산에만 적용하고, 표시되는 시도 횟수는 원본 값을 유지한다

> 단순하게 사용자 측면에서 바라봤을 때, 유사도가 높을수록 좋은 랭킹이 되는 것이 직관적이라고 생각했다. 그리고 100% 달성자 중에서는 선착순이 공정하다고 판단했다. 또한, 95~99.9% 달성자들 사이에서는 유사도가 가장 중요하지만, 동점자가 많을 수 있기 때문에 시도 횟수와 경과 시간으로 추가적으로 순위를 매기는 방식으로 결정했다.

### SSE 실시간 랭킹

WebSocket 대신 SSE를 선택한 이유:

- **단방향**: 랭킹은 서버 → 클라이언트 단방향 푸시만 필요
- **단순성**: HTTP 기반이므로 Nginx 프록시 설정이 간단 (버퍼링 OFF)
- **자동 재연결**: 브라우저 `EventSource` API가 자동 재연결 지원

이벤트 종류: `RANKING_UPDATE` (랭킹 변경), `DAY_CHANGE` (자정 문제 전환), `ANNOUNCEMENT` (공지), `HEARTBEAT` (10초 간격, 연결 수 포함).
100ms 디바운스로 짧은 시간 내 다수 변경을 한 번에 전송한다.

> 버퍼링을 만약 끄지 않는다면, 랭킹 업데이트가 발생해도 Nginx가 데이터를 버퍼링하여 클라이언트에 즉시 전달되지 않을 수 있다. 이로 인해 랭킹이 실시간으로 반영되지 않는 문제가 발생할 수 있다.

> 웹소켓을 사용해 유사도 입력과 랭킹 업데이트같은 이벤트들을 묶어서 처리할 수도 있었지만, 랭킹 업데이트는 단방향 푸시만 필요하고, 웹소켓은 설정과 관리가 더 복잡하기 때문에, 단순히 SSE로 구현하는 것이 충분하다고 판단했다. 추후 양방향 통신이 필요한 기능이 추가된다면 웹소켓 도입을 재검토해볼 수 있다.

## Rate Limiting

Bucket4j 인메모리 토큰 버킷을 사용한다. 단일 서버이므로 분산 Rate Limiter가 불필요하다.

| 그룹  | 대상                  | 제한    | 식별                     |
| ----- | --------------------- | ------- | ------------------------ |
| GUESS | `POST /daily/guess`   | 40/min  | 로그인: userId, 익명: IP |
| READ  | `GET` 엔드포인트      | 120/min | 로그인: userId, 익명: IP |
| SSE   | `GET /ranking/stream` | 10/min  | 로그인: userId, 익명: IP |
| AUTH  | `/auth/**`            | 10/min  | 항상 IP                  |
| ADMIN | `/admin/**`           | 120/min | userId                   |

- 5분마다 미사용 버킷 정리 (스케줄러)
- 429 응답 시 `Retry-After` 헤더 포함
- 성공 시 `X-Rate-Limit-Remaining` 헤더로 남은 토큰 수 반환

> 제한값을 넉넉하게 잡은 이유는, 너무 낮으면 정상적인 사용자가 제한에 걸려 불편함을 겪을 수 있기 때문이다. 특히 추측 API는 게임의 핵심 기능이므로, 충분한 시도 기회를 제공하는 것이 중요하다고 판단했다. 물론, 악의적인 사용자가 무제한으로 시도하는 것을 방지하기 위해 적절한 수준의 제한을 설정했다.

## Resilience4j Circuit Breaker

AI 서비스 호출을 Circuit Breaker로 감싼다. AI 서비스 장애 시 빠르게 실패하여 게임 서비스 전체의 응답 시간 저하를 방지한다.

```
CLOSED → 정상 호출
OPEN → AI 서비스 연속 실패 시, 즉시 AI_SERVICE_UNAVAILABLE 에러 반환
HALF_OPEN → 일정 시간 후 재시도
```

## 알림 (Discord 웹훅)

운영 중 중요 이벤트를 Discord 웹훅으로 알리도록 설계했다.

| 출처                         | 레벨          | 호출 방식                               | 멘션       |
| ---------------------------- | ------------- | --------------------------------------- | ---------- |
| 자정 스케줄러 결과           | `INFO`        | `DailySentenceScheduler.notifyDiscord`  | 없음       |
| 어드민 감사 로그             | severity 기반 | `AuditLogNotificationListener` (이벤트) | CRITICAL만 |
| `GlobalExceptionHandler` 500 | `HIGH`        | `ServerErrorNotifier.notify`            | Role 멘션  |

### 레벨 체계

`NotificationLevel { HIGH, INFO }` 2단계. Discord content/allowed_mentions/embed color 매핑은 `DiscordWebhookClient` 내부에 정의해둠.

| 레벨 | 색상             | content                       | allowed_mentions            |
| ---- | ---------------- | ----------------------------- | --------------------------- |
| HIGH | `#E67E22` (주황) | `<@&{roleId}>` 또는 빈 문자열 | `roles: [roleId]` 또는 none |
| INFO | `#3498DB` (파랑) | 빈 문자열                     | none                        |

`mentionRoleId` 미설정 시 HIGH도 멘션 없이 전송하게 해두었다.

`CRITICAL` (`@everyone`) 레벨은 설계 초기에 검토했으나 **현재 @everyone 멘션을 날릴만한 적절한 이벤트가 없어서 YAGNI원칙에 의거하여 보류.** 실사용 시나리오 (DB 연결 불가, Circuit breaker OPEN 등) 확정 시 enum 값 추가 + switch 분기 작업으로 도입 가능하다.

### AuditSeverity

감사 로그의 알림 레벨은 `AuditAction`마다 고정된 `AuditSeverity`로 결정한다. 기존에는 감사 로그 알림이 모두 `HIGH`(멘션 포함)였지만, 문장 등록이나 닉네임 강제 변경 같은 일상 운영 액션까지 멘션이 오면 알림 피로가 심해지기 때문에 2단계로 분리했다.

| Severity   | NotificationLevel | 멘션      | 해당 액션                                                                     |
| ---------- | ----------------- | --------- | ----------------------------------------------------------------------------- |
| `CRITICAL` | `HIGH`            | Role 멘션 | 긴급 교체, 역할 변경, 차단/해제, 강제 탈퇴, 랭킹 캐시 리셋, Rate Limit 초기화 |
| `ROUTINE`  | `INFO`            | 없음      | 문장 CRUD, CSV 업로드, 닉네임 강제, 프로필 강제 삭제, 공지 CRUD               |

`AuditAction` enum이 severity를 필드로 갖고, `AuditLogNotificationListener`에서 severity에 따라 `NotificationLevel`을 결정한다.

### 감사 로그 알림: 이벤트 기반 (AFTER_COMMIT)

`AdminAuditService.log()`가 `AuditLogRepository.save` 직후 `AuditLogEvent`를 발행한다. `AuditLogNotificationListener`는 `@TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=true)`로 수신 → Discord 전송.

- **AFTER_COMMIT 이유**: 감사 로그가 실제로 DB에 저장이 된 이후에만 알림이 나가야 DB와 알림의 정합성이 보장된다. TX 롤백 시 자동으로 이벤트도 취소되서 OK.
- **`fallbackExecution=true` 이유**: TX 없는 유닛 테스트에서도 리스너가 호출되도록 했다. (기존 `SseEventListener` 패턴과 일관성 유지)

### ERROR 알림: 서비스 추출 (직접 호출)

`GlobalExceptionHandler.handleUnexpectedException`이 `ServerErrorNotifier.notify(ex, request)`를 호출한다.

이벤트 기반이 아닌 이유:

- 서버 오류는 TX 밖에서 발생한다. `AFTER_COMMIT` 같은 phase 제어가 필요 없음.
- 쿨다운 판단을 호출 스레드에서 즉시 해야 Async 큐 적재 후 드롭의 비효율을 피할 수 있다.
- Handler가 알림 도메인 세부(embed 포맷, dedup 키, Discord API 제약 상수)까지 갖게 되면 SRP 위반. `ServerErrorNotifier`로 분리해 Handler는 "예외 → HTTP 응답 매핑"만 담당한다.

> 사실 처음에는 이벤트 기반 + GlobalExceptionHandler에서 예외 발생 시점에 이벤트를 발행하는 방식으로 구현하려고 했지만, 구현하고 보니, 예외 핸들러에서는 하나의 책임만 가지는 것이 좋겠다는 생각이 들었다. 예외 핸들러가 알림 도메인까지 알게 되면, 예외 처리 로직과 알림 로직이 서로 얽히게 되어 유지보수가 어려워질 수 있다고 판단했다. 또한, 서버 오류는 트랜잭션 밖에서 발생하기 때문에, 이벤트의 phase 제어가 필요 없어서, 직접 호출하는 방식이 더 간단하고 효율적이라고 생각했다.

### 중복 억제 (Dedup)

`NotificationDeduplicationCache` (`ConcurrentHashMap<String, Instant>`):

- `shouldSend(key, cooldown)`: 원자적 `compute`로 마지막 전송 시각 조회/갱신. `AtomicBoolean` 반환으로 "쿨다운 내 재전송 여부" 판단
- 키 구조: `exceptionClassName:requestUri` - 동일 예외가 동일 경로에서 반복 발생 시 쿨다운 내 한 번만 알림하게끔 설계
- `@Scheduled` cleanup: 실행 주기는 `NOTIFICATION_DEDUP_CLEANUP_INTERVAL_MS`(기본 5분). 매 실행 시 마지막 전송 후 `errorAlert.cooldown × 2`가 경과한 엔트리를 제거

### Async 실행 풀

`DiscordWebhookExecutor` (core=1/max=2/queue=50) 전용 풀 `@Async("discordWebhookExecutor")` 주입.

HTTP I/O가 호출 스레드를 블로킹하지 않도록 분리했다.

혹시나 큐 고갈 시 `AbortPolicy` → 호출자 `try-catch`에서 warn 로그 후 알림 스킵

### 웹훅 URL/Role ID 미설정 동작

`DiscordWebhookClient.dispatch`가 `webhookUrl` 공백이면 skip. 테스트/로컬에서 전송 방지. 프로덕션 배포 시 설정해야함. `mentionRoleId` 미설정 시 HIGH 레벨도 멘션 없이 전송.

## 데이터 모델

### 시간 처리

`LocalDateTime` 금지. 모든 시간은 `Instant`(UTC)로 저장하고, KST 변환이 필요한 곳에서만 `TimeConstants.KST`를 사용한다. `LocalDate`는 날짜만 필요한 경우 (출제일, 스케줄)에 사용.

> 기본적으로 한국에서만 서비스 할 예정이라면, 서버 시간도 KST로 설정할 수 있지만, UTC로 저장하는 것이 더 표준적이고, 나중에 다른 시간대 지원이 필요할 때도 유연하게 대응할 수 있다고 판단했다.

> 세계 표준시인 UTC로 시간을 저장하면, 나중에 다른 시간대 지원이 필요할 때도 유연하게 대응할 수 있다. KST로 저장하면, 나중에 다른 시간대 지원이 필요할 때 문제가 될 수 있다.

> 실제로 이전의 개발 경험중에서 UTC와 KST 혼용으로 여러 버그를 경험한 적이 많다.

### JPA Auditing

엔티티 시간 필드(`createdAt`, `updatedAt`)를 Spring Data JPA Auditing으로 자동 관리한다. `@PrePersist`에서 `Instant.now()`를 직접 호출하면 테스트에서 시간을 제어할 수 없으므로, `Clock` 빈을 주입하는 구조를 택했다.

```java
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@RequiredArgsConstructor
public class AuditConfig {
  private final Clock clock;

  @Bean
  public DateTimeProvider auditingDateTimeProvider() {
    return () -> Optional.of(clock.instant());
  }
}
```

테스트에서는 `TestClock`을 주입해 시간을 고정하거나 원하는 시점으로 이동시킬 수 있다.

2단계 상속 구조로 엔티티별 필요에 맞게 선택한다.

| 기반 클래스                                    | 필드                         | 사용 엔티티                          |
| ---------------------------------------------- | ---------------------------- | ------------------------------------ |
| `BaseTimeEntity`                               | `createdAt` (불변)           | DailySentence, GameSession 외 대부분 |
| `BaseAuditableEntity` (extends BaseTimeEntity) | + `updatedAt` (수정 시 갱신) | Member, GameSession                  |

`SocialAccount`는 예외로 `connectedAt`이라는 비즈니스 의미의 시간 필드를 갖는다. Auditing이 아닌 `@Column` + `@PrePersist`에서 직접 설정하는 방식을 쓴다.

### Flyway 마이그레이션

V1~V17까지 적용. 기존 마이그레이션은 절대 수정하지 않고, 새 마이그레이션을 추가한다.

> 마이그레이션 파일은 `V{timestamp}__{description}.sql` 형식으로 작성한다. 예: `V20260407_01__add_user_email.sql`

> 개발을 진행하며, 데이터 모델이 변경될 때마다 새로운 마이그레이션 파일을 추가하여, 데이터베이스 스키마의 변경 이력을 명확하게 관리하기 위함이였다.

### 초기 데이터 시딩

Flyway는 DDL 전용으로 유지하고, DML(초기 admin 계정 + 기본 문장)은 Java 런타임 시딩으로 분리했다.

`InitialDataSeeder`가 `ApplicationReadyEvent`를 받아 문장 → admin 순서로 삽입한다.

- **실행 게이트**: 매 실행마다 `daily_sentences.count()`·`members.count()`를 각각 확인하고, 해당 테이블이 비어있을 때만 시딩한다. 둘 다 데이터가 있으면 파일 파싱/DB 쓰기 없이 즉시 return.
- **실행 순서**: `@Order(Ordered.HIGHEST_PRECEDENCE)`로 `DailySentenceScheduler.onApplicationReady`보다 선행. 문장 시딩 실패 시 fail-fast로 스케줄러 도달 전 서버 실행이 중단된다.
- **문장 소스**: `backend/src/main/resources/seed/sentences.txt` (UTF-8, 빈 줄/`#` skip). 파일 내 중복은 `HashSet`으로 거르기.
- **admin 계정**: `members` 테이블이 비어있고 `SEED_ADMIN_PASSWORD`(→ `app.seed.admin-password`)가 설정된 경우에만 생성. username은 고정 상수 `admin`, 닉네임은 `NicknameGenerator`가 생성한다. env 미설정 시 skip.
- **Fail-fast**: 시드 파일 누락, 파일에서 유효 문장 0개, 비밀번호 8자 미만, DB 오류는 `IllegalStateException`으로 서버 실행을 중단한다.
- **TX 경계**: `TransactionTemplate`로 DB 쓰기만 TX 안, BCrypt 해싱은 TX 밖(TX 내 I/O 분리 원칙 준수).
- **운영 권장**: 최초 admin 생성 후 `.env.prod`에서 `SEED_ADMIN_PASSWORD`를 제거해 평문 잔존을 방지한다.

> Flyway에 DML을 넣는 대안도 있었지만, BCrypt 해싱과 닉네임 생성 로직을 SQL로 구현하면 서비스 코드와 중복된다. Java 런타임 시딩으로 통합해서 `PasswordEncoder`·`NicknameGenerator` 같은 기존 컴포넌트를 재사용했다. 매 실행마다 파일을 재파싱하는 비용을 피하기 위해 `count() == 0` 상태에서만 시딩하는 방식을 택했다.

## API 문서화 (springdoc-openapi)

수기 작성 `docs/api-spec.md`를 springdoc-openapi로 대체했다.

### 이중 분리

- **런타임 Swagger UI** (`/swagger-ui.html`): 비인증 시 public 그룹만, ADMIN 로그인 시 admin 그룹 추가 노출
- **GitHub Pages 정적 사이트** (`docs-site/`): CI가 `docs-mode=true`로 전체 스펙(`/v3/api-docs/full`)을 생성해 `gh-pages`에 배포

### Role 기반 그룹 필터링

Spring Security role 기반으로 admin 그룹 접근을 제어한다.

- `GroupedOpenApi` admin 빈은 항상 등록하되, `/v3/api-docs/admin`은 `hasRole('ADMIN')`으로 보호
- `SwaggerConfigController`가 기본 `/v3/api-docs/swagger-config`를 대체해 `SecurityContextHolder` Principal 기반으로 그룹 목록 필터링
- 비ADMIN에게 admin 그룹명 자체가 드롭다운에 표시되지 않음

### CI 전용 모드 (`docs-mode=true`)

- `NoOpSimilarityService`(`@Primary` + `@ConditionalOnProperty`)로 AI 서비스 없이 Spring 컨텍스트 부팅
- `full` 그룹은 `@ConditionalOnProperty`(1차) + SecurityConfig `access` 체크(2차)로 이중 차단

### 어노테이션 Tier 2

- 컨트롤러: `@Tag(name)` + 메서드별 `@Operation(summary)`
- 에러: `@StandardErrorResponses` (400/401/404/429/503), `@AdminErrorResponses` (+403)
- 인증: `@SecurityRequirement(name="cookieAuth")`
- OAuth 합성 경로: `OpenApiCustomizer`로 PathItem 삽입 (컨트롤러 없는 Spring Security 엔드포인트)

---

_마지막 업데이트: 2026-10-11_
