package com.gakkaweo.backend.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.infra.ai.client.AiServiceClient;
import com.gakkaweo.backend.infra.ai.client.dto.SimilarityResponse;
import com.gakkaweo.backend.infra.ai.exception.AiServiceException;
import com.gakkaweo.backend.infra.ai.service.SimilarityService;
import com.gakkaweo.backend.infra.ai.service.TextNormalizer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@DisplayName("SimilarityService 단위 테스트")
class SimilarityServiceTest {

  private AiServiceClient aiServiceClient;
  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOps;
  private CircuitBreakerRegistry circuitBreakerRegistry;
  private SimilarityService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    aiServiceClient = Mockito.mock(AiServiceClient.class);
    redisTemplate = Mockito.mock(StringRedisTemplate.class);
    valueOps = Mockito.mock(ValueOperations.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOps);

    circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    service =
        new SimilarityService(
            aiServiceClient, new TextNormalizer(), redisTemplate, circuitBreakerRegistry);
  }

  @Test
  @DisplayName("testSimilarity - 정상 호출 시 BigDecimal 반환")
  void testSimilarity_정상() {
    when(aiServiceClient.calculateSimilarity(anyString(), anyString()))
        .thenReturn(new SimilarityResponse(73.456));

    BigDecimal score = service.testSimilarity("원문입니다", "추측 입력");

    assertThat(score).isEqualByComparingTo("73.5");
  }

  @Test
  @DisplayName("testSimilarity - 정규화 후 빈 문자열이면 INVALID_GUESS_TEXT")
  void testSimilarity_빈입력() {
    assertThatThrownBy(() -> service.testSimilarity("원문", "!!!"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GUESS_TEXT);
  }

  @Test
  @DisplayName("calculateSimilarity - 캐시 HIT 시 AI 호출 안 함")
  void calculate_캐시HIT() {
    when(valueOps.get(anyString())).thenReturn("88.0");

    BigDecimal score = service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10));

    assertThat(score).isEqualByComparingTo("88.0");
    verify(aiServiceClient, never()).calculateSimilarity(anyString(), anyString());
    verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("calculateSimilarity - 캐시 MISS 시 AI 호출 + 캐시 저장")
  void calculate_캐시MISS() {
    when(valueOps.get(anyString())).thenReturn(null);
    when(aiServiceClient.calculateSimilarity(anyString(), anyString()))
        .thenReturn(new SimilarityResponse(42.1));

    BigDecimal score = service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10));

    assertThat(score).isEqualByComparingTo("42.1");
    verify(valueOps).set(anyString(), anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("calculateSimilarity - 빈 guess INVALID_GUESS_TEXT")
  void calculate_빈입력() {
    assertThatThrownBy(() -> service.calculateSimilarity(1L, "???", "원문", Duration.ofMinutes(10)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_GUESS_TEXT);
  }

  @Test
  @DisplayName("calculateSimilarity - AiServiceException 시 AI_SERVICE_UNAVAILABLE")
  void ai_예외() {
    when(valueOps.get(anyString())).thenReturn(null);
    when(aiServiceClient.calculateSimilarity(anyString(), anyString()))
        .thenThrow(new AiServiceException("다운", new RuntimeException()));

    assertThatThrownBy(() -> service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
  }

  @Test
  @DisplayName("서킷 OPEN - AI_SERVICE_UNAVAILABLE")
  void 서킷_OPEN() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("aiService");
    cb.transitionToOpenState();
    when(valueOps.get(anyString())).thenReturn(null);

    assertThatThrownBy(() -> service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10)))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.AI_SERVICE_UNAVAILABLE);
    verify(aiServiceClient, never()).calculateSimilarity(anyString(), anyString());
  }

  @Test
  @DisplayName("Redis get 실패 - AI 호출 후 정상 반환")
  void redis_get_실패() {
    when(valueOps.get(anyString())).thenThrow(new QueryTimeoutException("timeout"));
    when(aiServiceClient.calculateSimilarity(anyString(), anyString()))
        .thenReturn(new SimilarityResponse(60.0));

    BigDecimal score = service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10));

    assertThat(score).isEqualByComparingTo("60.0");
  }

  @Test
  @DisplayName("Redis set 실패 - 점수는 정상 반환")
  void redis_set_실패() {
    when(valueOps.get(anyString())).thenReturn(null);
    when(aiServiceClient.calculateSimilarity(anyString(), anyString()))
        .thenReturn(new SimilarityResponse(55.5));
    Mockito.doThrow(new QueryTimeoutException("timeout"))
        .when(valueOps)
        .set(anyString(), anyString(), any(Duration.class));

    BigDecimal score = service.calculateSimilarity(1L, "추측", "원문", Duration.ofMinutes(10));

    assertThat(score).isEqualByComparingTo("55.5");
  }
}
