package com.gakkaweo.backend.support;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.infra.ai.service.SimilarityClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Primary
public class TestSimilarityClient implements SimilarityClient {

  private final Map<String, BigDecimal> programmedScores = new HashMap<>();
  private BigDecimal defaultScore = new BigDecimal("50.0");
  private boolean throwUnavailable = false;

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
  }

  @Override
  public BigDecimal testSimilarity(String sentenceText, String guessText) {
    return resolveScore(guessText);
  }

  @Override
  public BigDecimal calculateSimilarity(
      Long sentenceId, String guessText, String sentenceText, Duration cacheTtl) {
    return resolveScore(guessText);
  }

  private BigDecimal resolveScore(String guessText) {
    if (throwUnavailable) {
      throwUnavailable = false;
      throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
    String normalized = normalizeKey(guessText);
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_GUESS_TEXT);
    }
    return programmedScores.getOrDefault(normalized, defaultScore);
  }

  private String normalizeKey(String text) {
    if (text == null) {
      return "";
    }
    return text.replaceAll("[^가-힣a-zA-Z0-9\\s]", "").replaceAll("\\s+", " ").trim();
  }
}
