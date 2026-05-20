# AI 서비스 의사결정

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
- **의존성**: 외부 서비스 장애 시 게임 전체가 중단. 따라서 자체 모델로 안정성 확보

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

FE, BE, AI 서비스 각각에서 정규화를 수행한다. 동일한 로직(`[^가-힣a-zA-Z0-9\s]` 제거 + 공백 정규화)이지만 목적이 다르다:

| 계층                      | 정규화 목적                                                           |
| ------------------------- | --------------------------------------------------------------------- |
| FE (`normalizeGuessText`) | 서버 호출 전 선검증. 빈 문자열이면 서버 요청 차단                     |
| BE (`TextNormalizer`)     | 비즈니스 규칙 검증. 정규화 후 빈 문자열이면 `INVALID_GUESS_TEXT` 에러 |
| AI (`normalize_text`)     | 임베딩 품질 보장. 특수문자가 유사도 계산에 노이즈를 주는 것을 방지    |

## 캐싱 전략

### AI 서비스 내부: LRU 캐시

```python
@lru_cache(maxsize=32)
def encode_text(text: str) -> np.ndarray:
    return model.encode(text)
```

최근 32개 텍스트의 임베딩을 메모리에 캐시한다. 정답 문장은 하루 동안 동일하므로 캐시 히트율이 높다.

예를들어, 오늘의 문장이 `친구들과 밖에서 축구를 했다` 라면, 유사도 비교를 위해, `친구들과 밖에서 축구를 했다` → 임베딩 벡터로 변환하는 과정이 필요하다.

이때, `encode_text` 함수가 호출되고, 결과가 LRU 캐시에 저장된다. 이후 동일한 문장에 대한 유사도 계산이 필요할 때, 캐시에서 빠르게 임베딩을 가져올 수 있다.

따라서, 오늘의 문장은 캐시 히트가 되고, 사용자의 추측만 임베딩 계산만 새로 수행하면 된다.

### Backend: Redis 캐시

`SimilarityService`에서 `sentenceId:hash(normalizedGuess)` 키로 유사도 결과를 Redis에 캐시한다.

TTL은 자정까지 남은 시간으로 설정해주고, 같은 추측을 여러 사용자가 제출하면 AI 서비스를 재호출하지 않는다.

따라서, 불필요한 AI 서비스 호출을 줄여서 응답 속도를 개선하고, AI 서비스의 부하를 낮춘다. (비록 Redis에 캐시를 저장함으로써 약간의 메모리 사용이 증가하지만, 미미하다고 판단했다.)

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

_마지막 업데이트: 2026-05-14_
