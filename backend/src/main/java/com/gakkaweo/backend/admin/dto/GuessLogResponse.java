package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.member.entity.Member;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuessLogResponse(List<GuessLogEntry> logs) {

  public record GuessLogEntry(
      UUID memberPublicId,
      String nickname,
      String guessText,
      BigDecimal similarity,
      int attemptNumber,
      Instant createdAt) {

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
