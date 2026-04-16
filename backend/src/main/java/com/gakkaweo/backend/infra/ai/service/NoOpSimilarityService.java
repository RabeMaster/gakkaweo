package com.gakkaweo.backend.infra.ai.service;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnProperty(name = "app.openapi.docs-mode", havingValue = "true")
public class NoOpSimilarityService implements SimilarityClient {

  @Override
  public BigDecimal testSimilarity(String sentenceText, String guessText) {
    return BigDecimal.ZERO;
  }

  @Override
  public BigDecimal calculateSimilarity(
      Long sentenceId, String guessText, String sentenceText, Duration cacheTtl) {
    return BigDecimal.ZERO;
  }
}
