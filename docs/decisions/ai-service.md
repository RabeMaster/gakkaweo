# AI 서비스 의사결정

2026-10-05 갱신: 입력·점수 계약과 모델 생명주기·동시 캐시 처리를 보강했습니다. 아래 과거 성능 설명은 현재 환경에서 재측정한 값이 아닙니다.

## 개요

별도의 FastAPI 마이크로서비스로 AI 유사도 계산을 분리했다. Backend(Spring Boot)에서 HTTP로 호출.

### 왜 별도 서비스로 분리했는가

- **런타임 분리**: PyTorch + sentence-transformers는 Python 생태계. Java에서 직접 실행하면 JNI 오버헤드와 메모리 관리가 복잡해진다.

  > 실제로 Java에서 Python 모델을 호출하는 라이브러리를 테스트했는데, 보일러플레이트도 많았고, 성능 쪽 부분에서 별도로 띄웠을 때보다 2~3배 느렸다.

  > (이건 코딩을 잘못해서일 수도 있지만, 어쨌든 FastAPI로 간단히 HTTP API로 분리했을 때 성능이 충분히 좋았기에, 분리하는 쪽으로 결정했다.)

- **독립 배포**: 모델 업데이트나 Python 버전 변경이 Backend 배포에 영향을 주지 않는다.
  > AI 모델은 빠르게 발전하는 분야라, 향후 더 나은 모델이 나오면 AI 서비스만 업데이트하면 된다. Backend는 API만 맞추면 문제가 없다.
- **리소스 격리**: 모델 로딩에 1~2GB 메모리가 필요. 혹시 메모리 누수가 있더라도 전체 시스템에 영향을 주지 않는다.
  > AI 모델이 메모리를 많이 쓰는데, 만약 Backend와 같이 띄우면 Backend가 불안정해질 수 있다. 별도 서비스로 띄우면 AI 서비스에 문제가 생겨도 Backend는 계속 운영할 수 있다. (유사도 계산이 실패하면 의미는 없지만...)

## 모델 선택: `jhgan/ko-sbert-sts`

한국어 문장 유사도 측정에 특화된 Sentence-BERT 모델.

- **기반**: `klue/roberta-base`를 KorSTS(한국어 의미 유사도) 데이터셋으로 fine-tuning
- **출력**: 768차원 문장 임베딩 → 코사인 유사도로 비교
- **크기**: 443MB - CPU 추론에 적합한 크기
- **선택 이유**: 한국어 STS 벤치마크에서 높은 성능. 글자 매칭이 아닌 의미 기반 비교가 게임의 핵심 메카닉이므로, 문장 단위 임베딩 모델이 적합

### 왜 OpenAI API가 아닌가

- **비용**: 매 추측마다 API 호출 시 비용이 누적. 자체 모델은 초기 로딩 후 무료
- **지연 시간**: 로컬 추론 < 100ms vs 외부 API ~500ms+
- **의존성**: 외부 서비스 장애 시 게임 전체가 중단. 자체 모델이라 이 위험이 없다

## 유사도 계산 파이프라인

```
사용자 입력
  → 텍스트 정규화 (FE: normalizeGuessText)
    → 텍스트 정규화 (BE: TextNormalizer)
      → AI Service 호출 (HTTP POST /similarity)
        → 텍스트 정규화 (AI: normalize_text)
          → 문장 임베딩 (sentence-transformers encode)
            → 코사인 유사도 계산
              → 0~100 스케일 변환 (소수점 1자리)
```

### 3중 정규화의 이유

FE, BE, AI 서비스는 NFC 정규화 후 완성 한글·ASCII 영문·숫자·Unicode `White_Space`를 남기고 공백을 한 칸으로 축약한다.

- 언어별 기본 `\s`의 차이를 그대로 사용하지 않는다.
- BOM(U+FEFF)과 U+001C~001F는 공백으로 바꾸지 않고 제거한다.

| 계층                      | 정규화 목적                                           |
| ------------------------- | ----------------------------------------------------- |
| FE (`normalizeGuessText`) | 서버 호출 전 선검증. 정규화 후 2자 미만이면 요청 차단 |
| BE (`TextNormalizer`)     | 정규화 후 2자 미만이면 `INVALID_GUESS_TEXT` 에러      |
| AI (`normalize_text`)     | 요청 검증 중 정규화하고 2자 미만이면 422 반환         |

- 원문 요청의 2~200자 제한은 정규화 전에 적용한다.
- 특수문자를 제거하면 200자 이하가 된다는 이유로 긴 원문을 허용하지 않는다.
- 유효 입력은 정규화 후 임베딩에 전달한다.

### 점수와 오류 계약

- 코사인 유사도가 유한한 값인지 확인한 뒤 0~1로 제한하고 100을 곱해 소수점 1자리로 반올림한다.
- AI 응답 스키마와 BE HTTP 클라이언트는 유한한 0~100 점수만 허용한다.
- BE DTO는 `Double`을 사용하여 누락·null을 0점으로 오인하지 않는다.
- 준비되지 않은 모델이나 추론 실패는 503이다.
- 내부 오류 메시지와 입력 문장을 응답에 노출하지 않는다.
- 예외 안내와 요청 검증의 `detail`/`msg`는 한국어로 제공한다.
- 422 응답의 필드 위치(`loc`)와 오류 식별자(`type`), 점수·상태 필드 등 처리용 키는 유지한다.

## 모델 생명주기와 준비 상태

- 모듈을 import할 때 모델을 로드하지 않는다.
- FastAPI `lifespan`에서 CPU 모델을 생성하고 warmup encode가 성공한 뒤 준비 상태를 공개한다.
- `/health`는 준비되지 않으면 503, 준비됐으면 200을 반환한다.
- 모델 로드·warmup 실패는 앱 시작을 실패시키며 종료 시 모델 참조와 LRU 캐시를 정리한다.

## 캐싱 전략

### AI 서비스 내부: LRU 캐시

- `encode_text`는 `RLock` 안에서 내부 `_cached_encode_text`의 `lru_cache(maxsize=32)`를 호출한다.
- 모델 load/unload에도 같은 잠금을 사용한다. 같은 미캐시 입력의 동시 요청은 encode를 한 번만 실행하며 다른 입력의 CPU encode도 직렬화한다.
- 실패한 encode는 캐시에 저장하지 않는다.

최근 32개 텍스트의 임베딩을 메모리에 캐시한다. 정답 문장은 하루 동안 동일하므로 캐시 히트율이 높다.

예를들어, 오늘의 문장이 `친구들과 밖에서 축구를 했다` 라면, 유사도를 비교하려면 `친구들과 밖에서 축구를 했다`를 임베딩 벡터로 변환하는 과정이 먼저 필요하다.

이때 `encode_text` 함수가 호출되고 결과가 LRU 캐시에 저장된다. 이후 같은 문장의 유사도를 다시 계산할 때는 캐시에서 임베딩을 바로 가져온다.

그래서 오늘의 문장은 캐시 히트가 되고, 새로 임베딩을 계산하는 건 사용자의 추측뿐이다.

### Backend: Redis 캐시

`SimilarityService`에서 `sentenceId:hash(normalizedGuess)` 키로 유사도 결과를 Redis에 캐시한다.

TTL은 자정까지 남은 시간으로 설정한다. 같은 추측을 여러 사용자가 제출해도 AI 서비스를 다시 호출하지 않는다.

캐시 값이 숫자가 아니거나 0~100 범위를 벗어나면 캐시 miss처럼 AI로 재계산하고 정상 결과를 덮어쓴다.

Redis 조회·저장 실패 시에도 기존 fallback을 유지한다. AI 호출 실패와 열린 Circuit Breaker는 `AI_SERVICE_UNAVAILABLE`로 처리한다.

이렇게 불필요한 AI 서비스 호출을 줄이면 응답 속도가 개선되고 AI 서비스의 부하도 낮아진다. (비록 Redis에 캐시를 저장함으로써 약간의 메모리 사용이 증가하지만, 미미하다고 판단했다.)

### 캐시 계층 요약

```
1. Redis 캐시 히트 → 즉시 반환 (DB/AI 호출 없음)
2. Redis 캐시 미스 → AI 서비스 호출 → 결과 Redis에 저장
3. AI 서비스 내 LRU 캐시 → 임베딩 재계산 방지
```

## 장애 대응: Circuit Breaker

Backend의 `SimilarityService`가 Resilience4j Circuit Breaker로 AI 서비스 호출을 감싼다.

```
CLOSED (정상)
  → AI 서비스 연속 실패 시 OPEN 전환
OPEN (차단)
  → 즉시 AI_SERVICE_UNAVAILABLE(503) 반환. 불필요한 대기 시간 제거
HALF_OPEN (재시도)
  → 일정 시간 후 제한적으로 호출 시도. 성공 시 CLOSED 복귀
```

## Docker 설정

```yaml
ai-service:
  image: ghcr.io/.../gakkaweo-ai-service:latest
  deploy:
    resources:
      limits:
        memory: 1536M # 모델 + PyTorch + 추론 버퍼 + 50% 여유
  volumes:
    - hf-model-cache:/root/.cache/huggingface # 모델 재다운로드 방지
  healthcheck:
    start_period: 120s # 최초 모델 로딩 대기
```

- CPU 전용 PyTorch 사용 (`torch` CPU wheel). GPU 없는 홈서버 환경에 맞춤
- 모델 캐시 볼륨으로 컨테이너 재시작 시 재다운로드 방지
- 추후 측정을 통해 1GB로도 충분하면 메모리 제한 조정 가능. 혹은 피크가 1.4GB 이상이면 상향 조정 OR 최적화 OR 모델 교체 검토

---

## 경량 회귀 검사

FE `pnpm test`는 정규화 입력 사례를 검사한다.

AI는 `requirements-test.txt` 설치 후 `python -m unittest discover -s tests -v`로 API·생명주기·동시 캐시·정규화를 검사한다.

모델 생성과 코사인 계산을 mock하므로 PyTorch나 weights 다운로드가 필요하지 않다.
실제 모델의 정확도·CPU 성능·메모리 및 운영 환경을 검증한 결과로 해석하지 않는다.

_마지막 업데이트: 2026-10-05_
