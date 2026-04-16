package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "사용자 게임 이력")
public record UserGameHistoryResponse(
    @Schema(description = "게임 이력 목록") List<GameHistoryEntry> history) {

  @Schema(description = "게임 이력 항목")
  public record GameHistoryEntry(
      @Schema(description = "게임 날짜", example = "2026-04-17") LocalDate date,
      @Schema(description = "문장", example = "오늘 날씨가 좋다") String sentence,
      @Schema(
              description = "게임 상태",
              allowableValues = {"IN_PROGRESS", "CLEARED", "EXPIRED"},
              example = "CLEARED")
          String gameStatus,
      @Schema(description = "최고 유사도", example = "95.42") BigDecimal bestSimilarity,
      @Schema(description = "시도 횟수", example = "12") int attemptCount,
      @Schema(description = "최종 순위", nullable = true, example = "3") Integer finalRank,
      @Schema(description = "클리어 시각", nullable = true, example = "2026-04-17T06:30:00Z")
          Instant clearedAt) {

    public static GameHistoryEntry from(GameSession session) {
      return new GameHistoryEntry(
          session.getSentence().getUsedAt(),
          session.getSentence().getSentence(),
          session.getStatus().name(),
          session.getBestSimilarity(),
          session.getAttemptCount(),
          session.getFinalRank(),
          session.getClearedAt());
    }
  }
}
