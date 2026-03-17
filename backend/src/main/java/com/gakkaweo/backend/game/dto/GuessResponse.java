package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record GuessResponse(
    BigDecimal similarity,
    Integer attemptNumber,
    boolean isCorrect,
    String gameStatus,
    Instant timestamp) {}
