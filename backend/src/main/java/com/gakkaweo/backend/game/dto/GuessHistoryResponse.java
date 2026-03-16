package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GuessHistoryResponse(List<GuessEntry> guesses) {

  public record GuessEntry(
      String guessText, BigDecimal similarity, int attemptNumber, LocalDateTime createdAt) {}
}
