package com.gakkaweo.backend.game.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "게임 상태 응답")
public record GameStatusResponse(
    @Schema(description = "문제 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID sentenceId,
    @Schema(
            description = "게임 상태",
            allowableValues = {"IN_PROGRESS", "CLEARED", "EXPIRED"},
            example = "IN_PROGRESS")
        String gameStatus,
    @Schema(description = "최고 유사도", example = "85.7") BigDecimal bestSimilarity,
    @Schema(description = "시도 횟수", example = "12") int attemptCount,
    @Schema(description = "클리어 시각", nullable = true, example = "2026-04-17T12:00:00Z")
        Instant clearedAt) {

  public static GameStatusResponse from(DailySentence sentence, GameSession session) {
    return new GameStatusResponse(
        sentence.getPublicId(),
        session.getStatus().name(),
        session.getBestSimilarity(),
        session.getAttemptCount(),
        session.getClearedAt());
  }

  public static GameStatusResponse empty(UUID sentenceId) {
    return new GameStatusResponse(sentenceId, null, BigDecimal.ZERO, 0, null);
  }
}
