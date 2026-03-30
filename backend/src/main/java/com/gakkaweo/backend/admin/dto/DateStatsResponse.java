package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DateStatsResponse(
    LocalDate date,
    String sentence,
    long totalParticipants,
    long clearedCount,
    double clearRate,
    BigDecimal avgSimilarity,
    double avgAttemptCount) {

  public static DateStatsResponse from(
      LocalDate date,
      DailySentence sentence,
      long totalParticipants,
      long clearedCount,
      BigDecimal avgSimilarity,
      double avgAttemptCount) {
    double clearRate = totalParticipants > 0 ? (double) clearedCount / totalParticipants * 100 : 0;
    return new DateStatsResponse(
        date,
        sentence.getSentence(),
        totalParticipants,
        clearedCount,
        clearRate,
        avgSimilarity,
        avgAttemptCount);
  }
}
