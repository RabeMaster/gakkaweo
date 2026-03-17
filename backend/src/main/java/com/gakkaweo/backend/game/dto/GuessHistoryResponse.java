package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GuessHistoryResponse(List<GuessEntry> guesses) {

  public record GuessEntry(
      String guessText, BigDecimal similarity, int attemptNumber, Instant createdAt) {}
}
