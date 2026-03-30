package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UserGameHistoryResponse(List<GameHistoryEntry> history) {

  public record GameHistoryEntry(
      LocalDate date,
      String sentence,
      String gameStatus,
      BigDecimal bestSimilarity,
      int attemptCount,
      Integer finalRank,
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
