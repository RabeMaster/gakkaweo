# DB 인덱스 개선 - V15

## 왜 했나

실제 성능 문제는 아직까지는 없었다. 2026-04-25 기준 프로덕션의 현재 가장 큰 테이블(`guess_history`)이 12,573행이고 모든 쿼리가 1ms 미만이다.

다만 스키마를 훑어보니 두 가지 이슈가 있었고, 데이터가 늘기 전에 선제로 메웠다.

1. **외래키 컬럼 3개에 인덱스가 없다.** PostgreSQL은 외래키를 선언해도 자동으로 인덱스를 만들지 않는다. 회원 탈퇴처럼 부모 행을 지우는 순간 자식 테이블을 끝까지 훑는다.
2. **"필터 + 정렬" 쿼리가 기존 단일 인덱스로는 절반만 해결된다.** 어드민 사용자 목록이나 감사 로그처럼 WHERE와 ORDER BY가 함께 걸리는 쿼리는 인덱스로 행을 걸러도 정렬은 별도로 돌아간다.

이번 변경의 이유는 수치적인 개선이라기보다 "데이터가 10배, 100배가 돼도 어느정도 속도를 유지하는 준비"라고 보면 되겠다.

## 추가한 인덱스 7개

| 테이블 / 컬럼                               | 어디에 쓰이는가                                              |
| ------------------------------------------- | ------------------------------------------------------------ |
| `sentence_uploads(admin_id)`                | 어드민 탈퇴 시 업로드 이력 익명화, 회원 DELETE의 외래키 확인 |
| `social_accounts(member_id)`                | 소셜 계정 조회, 탈퇴 시 일괄 삭제                            |
| `refresh_tokens(member_id)`                 | 로그아웃 시 멤버 토큰 일괄 무효화                            |
| `game_sessions(member_id, created_at DESC)` | 마이페이지 게임 이력, 어드민 사용자 상세                     |
| `audit_logs(action, created_at DESC)`       | 어드민 감사 로그 action 필터 + 최신순                        |
| `guess_history(session_id, attempt_number)` | 세션별 추측 히스토리 재생                                    |
| `members(banned, created_at DESC)`          | 어드민 사용자 목록 차단/미차단 필터                          |

`guess_history`는 기존 단일 인덱스 `(session_id)`가 새 복합 인덱스의 앞부분에 이미 포함되므로 DROP 후 교체했다. 둘 다 두면 쓰기 때마다 같은 정보를 두 번 저장할 뿐이다.

제외한 항목: `daily_sentences.scheduled_at`, `daily_sentences.used_at`, `members.nickname`은 `UNIQUE` 제약이 걸려 있어 이미 자동 인덱스가 있다.

## 용어

아래 실측 표를 읽을 때 알고 있으면 좋은 단어들. PostgreSQL 공식 문서의 [Using EXPLAIN](https://www.postgresql.org/docs/current/using-explain.html)에 자세히 나와 있다.

> **Seq Scan (Sequential Scan)** - 인덱스를 쓰지 않고 테이블의 모든 행을 순서대로 훑는다.
>
> - 좋을 때: 작은 테이블, 또는 대부분의 행을 반환하는 쿼리. 연속 읽기라 I/O 효율이 좋고 인덱스 오버헤드가 없다.
> - 나쁠 때: 큰 테이블에서 소수 행만 고를 때. 시간이 테이블 크기에 비례(O(n))해 대부분의 작업이 낭비된다.
>
> **Index Scan** - 인덱스를 따라가며 매칭되는 행을 바로 읽는다.
>
> - 좋을 때: 소수 행 조회, 그리고 인덱스 순서가 정렬 방향과 일치해 ORDER BY를 인덱스만으로 해결할 수 있을 때 (추가 Sort 노드가 사라진다).
> - 나쁠 때: 반환 행이 많은 경우. 매 행마다 인덱스 → 힙을 랜덤 I/O로 왔다 갔다 해서 Bitmap 방식보다 느릴 수 있다.
>
> **Bitmap Index Scan + Bitmap Heap Scan** - 인덱스에서 매칭되는 행 위치를 비트맵으로 먼저 모은 뒤, 디스크 효율이 좋은 순서로 한 번에 테이블을 읽는다.
>
> - 좋을 때: 반환 행이 수십~수천 건 단위일 때. 여러 인덱스를 AND/OR로 결합할 수도 있다.
> - 나쁠 때: 인덱스 순서가 보존되지 않아 ORDER BY에 Sort가 따라붙는다. 비트맵이 `work_mem`을 넘으면 lossy 모드로 떨어져 재검사 비용이 든다.
>   work_mem이 무엇이냐면, PostgreSQL이 정렬이나 해시 조인 같은 작업을 메모리에서 처리할 때 사용하는 임시 공간이다.
>   인덱스 스캔이 반환하는 행 위치를 비트맵으로 저장하는데, 이 비트맵이 너무 커서 work_mem을 초과하면 "lossy" 모드로 전환된다.
>   이 경우 PostgreSQL은 비트맵에 정확한 행 위치 대신 대략적인 페이지 위치만 저장한다.
>   그래서 나중에 테이블을 읽을 때 해당 페이지의 모든 행을 다시 검사해야 해서 성능이 크게 떨어진다.
>
> **top-N heapsort** - `ORDER BY ... LIMIT N`에서 쓰는 정렬 방식. 전체를 정렬하지 않고 크기 N짜리 힙만 유지하면서 한 번 훑어 상위 N개만 뽑는다.
>
> - 좋을 때: LIMIT이 작을 때. 메모리/시간 모두 일반 정렬보다 적게 든다.
> - 나쁠 때: LIMIT이 없거나 N이 전체 크기에 근접할 때. 또, 입력 범위 자체는 여전히 다 훑어야 해서 "정렬 대상이 많다"는 비용은 그대로다.

## 실측

로컬 Testcontainers(PostgreSQL 16-alpine)에 목데이터를 넣고 V15 적용 전후를 비교했다.

규모: members 5,000명(차단된 사용자 1,500명) / game_sessions 20,000 / guess_history 200,000 / audit_logs 10,000 / social_accounts / refresh_tokens 각 5,000 / sentence_uploads / daily_sentences 각 500.

| 쿼리                                        | 전                                                                                                 | 후                                            | 비고                                      |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------- | --------------------------------------------- | ----------------------------------------- |
| `sentence_uploads(admin_id)`                | 0.166 ms - Seq Scan + top-N heapsort                                                               | 0.111 ms - Index Scan + top-N heapsort        | 정렬은 남지만 스캔 비용 제거              |
| `social_accounts(member_id)`                | 0.246 ms - Seq Scan (1건 찾자고 5,000행 전부 훑음)                                                 | 0.037 ms - Bitmap Index Scan                  |                                           |
| `refresh_tokens(member_id)`                 | 0.219 ms - Seq Scan (1건 찾자고 5,000행 전부 훑음)                                                 | 0.033 ms - Bitmap Index Scan                  |                                           |
| `game_sessions(member_id, created_at DESC)` | 0.116 ms - Bitmap Index Scan + Sort                                                                | 0.034 ms - Index Scan                         | 정렬이 인덱스에 녹아 Sort 노드 사라짐     |
| `audit_logs(action, created_at DESC)`       | 0.780 ms - Seq Scan + top-N heapsort                                                               | 0.531 ms - Bitmap Index Scan + top-N heapsort | Bitmap은 순서 보존 X → 정렬 일부 남음     |
| `guess_history(session_id, attempt_number)` | 0.037 ms - Bitmap Index Scan + Sort                                                                | 0.103 ms - Bitmap Index Scan + Sort           | 단일 → 복합 교체. 쓰기/디스크 이득이 본질 |
| `members(banned, created_at DESC)`          | 0.832 ms - Seq Scan + top-N heapsort (상위 20명 뽑자고 5,000명 전부 훑음, 1,500명은 차단이라 탈락) | 0.032 ms - Index Scan                         | 어드민 사용자 목록                        |

핵심은 개별 수치보다 **스캔 방식이 바뀌었다는 것**이다. Seq Scan은 테이블이 커질수록 선형으로 느려지고, Index/Bitmap Index Scan은 로그 수준으로 느려진다. 그래서 지금은 미미한 차이지만 데이터가 100만 행 단위가 되면 차이가 많이 날 것이라고 생각한다.

프로덕션은 이 목데이터보다 한참 작다. 따라서 배포 직후 눈에 띄는 속도 변화는 기대하지 않는다.

## 회귀 방지

`IndexPerformanceTest`(`@Tag("performance")`)가 EXPLAIN 플랜에 7개 인덱스 이름이 등장하는지 검사한다. 혹시나 V15를 수정하거나 쿼리 컬럼이 바뀌어 인덱스가 더 이상 선택되지 않으면 테스트가 깨진다. 측정 수치 자체는 환경에 따라 흔들리므로 assertion에 포함하지 않았다.

CI는 기본 `test`만 돌리고 이 테스트는 빠진다 (목데이터 삽입에 시간이 걸림). 로컬에서 `./gradlew performanceTest`로 실행한다.

## 배포 / 롤백

Flyway가 V15를 자동으로 실행한다. `CREATE INDEX CONCURRENTLY`는 Flyway의 기본 트랜잭션 방식과 충돌해 이번엔 일반 `CREATE INDEX`를 썼다. 현재 테이블 규모에선 락 시간이 수 밀리초라 문제없다. 만약 테이블이 대용량이 되면 무중단 배포 방식을 따로 준비해야 한다.

롤백은 V16에서 이번에 만든 7개 인덱스를 DROP하고 기존 `idx_guess_history_session`을 되살리면 끝난다. 인덱스는 쿼리 결과에 영향을 주지 않으므로 DDL 한 번으로 완전히 원상복구 가능.

## 다음 과제

- `pg_stat_statements` 활성화 - 운영에서 실제로 어떤 쿼리가 느린지 수집
  > **pg_stat_statements란 무엇인가**
  > PostgreSQL의 확장 모듈로, 실행된 SQL 쿼리에 대한 통계 정보를 수집하는 도구입니다.
  > 각 쿼리의 실행 횟수, 총 실행 시간, 평균 실행 시간, 최대/최소 실행 시간 등을 기록하여 데이터베이스 성능 분석에 도움을 줍니다.
  > 이를 통해 어떤 쿼리가 자주 실행되고 시간이 오래 걸리는지 파악할 수 있어, 인덱스 최적화나 쿼리 리팩토링 등의 성능 개선 작업에 활용됩니다.
- 대용량 테이블에서 락 없이 인덱스 만들기 (Flyway out-of-order + CONCURRENTLY)
- 미사용 인덱스 모니터링 (`pg_stat_user_indexes`)
  > **pg_stat_user_indexes란 무엇인가**
  > PostgreSQL의 시스템 뷰 중 하나로, 사용자 테이블에 존재하는 인덱스의 사용 통계를 제공합니다.
  > 각 인덱스에 대해 스캔 횟수, 튜플 읽기 수, 튜플 반환 수 등의 정보를 기록하여, 인덱스가 실제로 쿼리에서 얼마나 활용되고 있는지 파악할 수 있게 해줍니다.
  > 이를 통해 사용되지 않는 인덱스를 식별하여 제거하거나, 필요한 인덱스가 제대로 활용되고 있는지 모니터링할 수 있습니다.
