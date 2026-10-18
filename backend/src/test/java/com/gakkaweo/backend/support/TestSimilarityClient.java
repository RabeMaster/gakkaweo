package com.gakkaweo.backend.support;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.util.TextNormalizer;
import com.gakkaweo.backend.infra.ai.service.SimilarityClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Primary
public class TestSimilarityClient implements SimilarityClient {

  private final Map<String, BigDecimal> programmedScores = new HashMap<>();
  private BigDecimal defaultScore = new BigDecimal("50.0");
  private boolean throwUnavailable = false;
  private CountDownLatch delayEntered;
  private CountDownLatch delayProceed;

  public synchronized void delayNext(CountDownLatch entered, CountDownLatch proceed) {
    delayEntered = entered;
    delayProceed = proceed;
  }

  public void program(String guessText, BigDecimal score) {
    programmedScores.put(normalizeKey(guessText), score);
  }

  public void setDefaultScore(BigDecimal score) {
    this.defaultScore = score;
  }

  public void throwOnNext() {
    this.throwUnavailable = true;
  }

  public void reset() {
    programmedScores.clear();
    defaultScore = new BigDecimal("50.0");
    throwUnavailable = false;
    delayEntered = null;
    delayProceed = null;
  }

  @Override
  public BigDecimal testSimilarity(String sentenceText, String guessText) {
    return resolveScore(guessText);
  }

  @Override
  public BigDecimal calculateSimilarity(
      Long sentenceId, String guessText, String sentenceText, Duration cacheTtl) {
    CountDownLatch entered;
    CountDownLatch proceed;
    synchronized (this) {
      entered = delayEntered;
      proceed = delayProceed;
      delayEntered = null;
      delayProceed = null;
    }
    if (entered != null) {
      entered.countDown();
      try {
        if (!proceed.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Delayed similarity test timed out");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
    return resolveScore(guessText);
  }

  private BigDecimal resolveScore(String guessText) {
    if (throwUnavailable) {
      throwUnavailable = false;
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
    String normalized = normalizeKey(guessText);
    if (normalized.length() < 2) {
      throw new BusinessException(ErrorCode.INVALID_GUESS_TEXT);
    }
    return programmedScores.getOrDefault(normalized, defaultScore);
  }

  private String normalizeKey(String text) {
    if (text == null) {
      return "";
    }
    return new TextNormalizer().normalize(text);
  }
}
