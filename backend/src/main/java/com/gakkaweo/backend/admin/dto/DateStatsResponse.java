package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "날짜별 통계")
public record DateStatsResponse(
    @Schema(description = "날짜", example = "2026-04-17") LocalDate date,
    @Schema(description = "문장", example = "오늘 날씨가 좋다") String sentence,
    @Schema(description = "총 참여자 수", example = "42") long totalParticipants,
    @Schema(description = "클리어 수", example = "30") long clearedCount,
    @Schema(description = "클리어율", example = "71.4") double clearRate,
    @Schema(description = "평균 유사도", example = "78.50") BigDecimal avgSimilarity,
    @Schema(description = "평균 시도 횟수", example = "15.3") double avgAttemptCount) {

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
