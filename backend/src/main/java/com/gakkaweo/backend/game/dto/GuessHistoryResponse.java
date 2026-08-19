package com.gakkaweo.backend.game.dto;

import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "추측 히스토리 응답")
public record GuessHistoryResponse(@Schema(description = "추측 목록") List<GuessEntry> guesses) {

  @Schema(description = "추측 항목")
  public record GuessEntry(
      @Schema(description = "추측 텍스트", example = "오늘 날씨가 좋다") String guessText,
      @Schema(description = "유사도", example = "75.3") BigDecimal similarity,
      @Schema(description = "시도 번호", example = "3") int attemptNumber,
      @Schema(description = "추측 시각", example = "2026-04-17T12:00:00Z") Instant createdAt) {

    public static GuessEntry from(GuessHistory entity) {
      return new GuessEntry(
          entity.getGuessText(),
          entity.getSimilarity(),
          entity.getAttemptNumber(),
          entity.getCreatedAt());
    }
  }
}
