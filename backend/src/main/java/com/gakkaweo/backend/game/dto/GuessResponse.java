package com.gakkaweo.backend.game.dto;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "추측 응답")
public record GuessResponse(
    @Schema(description = "유사도 (0.0~100.0)", example = "75.3") BigDecimal similarity,
    @Schema(description = "시도 횟수 (익명은 null)", nullable = true, example = "5") Integer attemptNumber,
    @Schema(description = "정답 여부 (95% 이상)", example = "false") boolean isCorrect,
    @Schema(
            description = "게임 상태",
            nullable = true,
            allowableValues = {"IN_PROGRESS", "CLEARED", "EXPIRED"},
            example = "IN_PROGRESS")
        String gameStatus,
    @Schema(description = "서버 처리 시각", example = "2026-04-17T12:00:00Z") Instant timestamp) {

  public static GuessResponse from(
      BigDecimal similarity, GameSession session, boolean isCorrect, Instant timestamp) {
    return new GuessResponse(
        similarity, session.getAttemptCount(), isCorrect, session.getStatus().name(), timestamp);
  }

  public static GuessResponse fromAnonymous(
      BigDecimal similarity, boolean isCorrect, Instant timestamp) {
    return new GuessResponse(similarity, null, isCorrect, null, timestamp);
  }
}
