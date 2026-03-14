package com.gakkaweo.backend.infra.ai.service;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.infra.ai.client.AiServiceClient;
import com.gakkaweo.backend.infra.ai.client.dto.SimilarityResponse;
import com.gakkaweo.backend.infra.ai.config.AiServiceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SimilarityService {

  private static final String CACHE_KEY_PREFIX = "similarity:";

  private final AiServiceClient aiServiceClient;
  private final TextNormalizer textNormalizer;
  private final StringRedisTemplate redisTemplate;
  private final AiServiceProperties properties;
  private final CircuitBreaker circuitBreaker;

  public SimilarityService(
      AiServiceClient aiServiceClient,
      TextNormalizer textNormalizer,
      StringRedisTemplate redisTemplate,
      AiServiceProperties properties,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.aiServiceClient = aiServiceClient;
    this.textNormalizer = textNormalizer;
    this.redisTemplate = redisTemplate;
    this.properties = properties;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("aiService");
  }

  public BigDecimal calculateSimilarity(Long sentenceId, String guessText, String sentenceText) {
    String normalized = textNormalizer.normalize(guessText);
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_GUESS_TEXT);
    }
    String cacheKey = buildCacheKey(sentenceId, normalized);

    BigDecimal cached = getFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    BigDecimal score = callWithCircuitBreaker(sentenceText, normalized, cacheKey);
    saveToCache(cacheKey, score);
    return score;
  }

  private BigDecimal callWithCircuitBreaker(
      String sentenceText, String normalized, String cacheKey) {
    try {
      return circuitBreaker.executeSupplier(
          () -> {
            SimilarityResponse response =
                aiServiceClient.calculateSimilarity(sentenceText, normalized);
            return BigDecimal.valueOf(response.score());
          });
    } catch (Exception e) {
      return fallback(cacheKey, e);
    }
  }

  private BigDecimal fallback(String cacheKey, Throwable t) {
    log.warn("AI 서비스 호출 실패, 캐시 폴백 시도: {}", t.getMessage());

    BigDecimal cached = getFromCache(cacheKey);
    if (cached != null) {
      return cached;
    }

    throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
  }

  private String buildCacheKey(Long sentenceId, String normalizedText) {
    String hash = textNormalizer.hashForCache(normalizedText);
    return CACHE_KEY_PREFIX + sentenceId + ":" + hash;
  }

  private BigDecimal getFromCache(String cacheKey) {
    try {
      String value = redisTemplate.opsForValue().get(cacheKey);
      if (value != null) {
        return new BigDecimal(value);
      }
    } catch (Exception e) {
      log.warn("Redis 캐시 조회 실패: {}", e.getMessage());
    }
    return null;
  }

  private void saveToCache(String cacheKey, BigDecimal score) {
    try {
      Duration ttl = properties.getSimilarityCacheTtl();
      redisTemplate.opsForValue().set(cacheKey, score.toPlainString(), ttl);
    } catch (Exception e) {
      log.warn("Redis 캐시 저장 실패: {}", e.getMessage());
    }
  }
}
