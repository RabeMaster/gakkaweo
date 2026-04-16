package com.gakkaweo.backend.infra.ai.service;

import java.math.BigDecimal;
import java.time.Duration;

public interface SimilarityClient {

  BigDecimal testSimilarity(String sentenceText, String guessText);

  BigDecimal calculateSimilarity(
      Long sentenceId, String guessText, String sentenceText, Duration cacheTtl);
}
