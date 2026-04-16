package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "추측 로그")
public record GuessLogResponse(@Schema(description = "로그 목록") List<GuessLogEntry> logs) {

  @Schema(description = "추측 로그 항목")
  public record GuessLogEntry(
      @Schema(description = "회원 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
          UUID memberPublicId,
      @Schema(description = "닉네임", example = "가까워유저") String nickname,
      @Schema(description = "추측 텍스트", example = "날씨가 맑다") String guessText,
      @Schema(description = "유사도", example = "85.72") BigDecimal similarity,
      @Schema(description = "시도 번호", example = "3") int attemptNumber,
      @Schema(description = "추측 시각", example = "2026-04-17T06:30:00Z") Instant createdAt) {

    public static GuessLogEntry from(GuessHistory history) {
      Member member = history.getSession().getMember();
      return new GuessLogEntry(
          member != null ? member.getPublicId() : null,
          member != null ? member.getNickname() : "(익명)",
          history.getGuessText(),
          history.getSimilarity(),
          history.getAttemptNumber(),
          history.getCreatedAt());
    }
  }
}
