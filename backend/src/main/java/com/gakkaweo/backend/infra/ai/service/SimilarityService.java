package com.gakkaweo.backend.infra.ai.service;

import static com.gakkaweo.backend.common.redis.RedisKeyConstants.SIMILARITY_CACHE_PREFIX;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.infra.ai.client.AiServiceClient;
import com.gakkaweo.backend.infra.ai.client.dto.SimilarityResponse;
import com.gakkaweo.backend.infra.ai.exception.AiServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SimilarityService implements SimilarityClient {

  private final AiServiceClient aiServiceClient;
  private final TextNormalizer textNormalizer;
  private final StringRedisTemplate redisTemplate;
  private final CircuitBreaker circuitBreaker;

  public SimilarityService(
      AiServiceClient aiServiceClient,
      TextNormalizer textNormalizer,
      StringRedisTemplate redisTemplate,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.aiServiceClient = aiServiceClient;
    this.textNormalizer = textNormalizer;
    this.redisTemplate = redisTemplate;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiService");
  }

  @Override
  public BigDecimal testSimilarity(String sentenceText, String guessText) {
    String normalized = textNormalizer.normalize(guessText);
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_GUESS_TEXT);
    }
    return callWithCircuitBreaker(sentenceText, normalized);
  }

  @Override
  public BigDecimal calculateSimilarity(
      Long sentenceId, String guessText, String sentenceText, Duration cacheTtl) {
    String normalized = textNormalizer.normalize(guessText);
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_GUESS_TEXT);
    }
    String cacheKey = buildCacheKey(sentenceId, normalized);

    BigDecimal cached = getFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    BigDecimal score = callWithCircuitBreaker(sentenceText, normalized);
    saveToCache(cacheKey, score, cacheTtl);
    return score;
  }

  private BigDecimal callWithCircuitBreaker(String sentenceText, String normalized) {
    try {
      return circuitBreaker.executeSupplier(
          () -> {
            SimilarityResponse response =
                aiServiceClient.calculateSimilarity(sentenceText, normalized);
            return BigDecimal.valueOf(response.score()).setScale(1, RoundingMode.HALF_UP);
          });
    } catch (CallNotPermittedException e) {
      log.warn("서킷 브레이커 OPEN 상태: {}", e.getMessage(), e);
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    } catch (AiServiceException e) {
      log.warn("AI 서비스 호출 실패: {}", e.getMessage(), e);
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
  }

  private String buildCacheKey(Long sentenceId, String normalizedText) {
    String hash = textNormalizer.hashForCache(normalizedText);
    return SIMILARITY_CACHE_PREFIX + sentenceId + ":" + hash;
  }

  private BigDecimal getFromCache(String cacheKey) {
    try {
      String value = redisTemplate.opsForValue().get(cacheKey);
      if (value != null) {
        return new BigDecimal(value);
      }
    } catch (DataAccessException e) {
      log.warn("Redis 캐시 조회 실패: {}", e.getMessage(), e);
    }
    return null;
  }

  private void saveToCache(String cacheKey, BigDecimal score, Duration ttl) {
    try {
      redisTemplate.opsForValue().set(cacheKey, score.toPlainString(), ttl);
    } catch (DataAccessException e) {
      log.warn("Redis 캐시 저장 실패: {}", e.getMessage(), e);
    }
  }
}
