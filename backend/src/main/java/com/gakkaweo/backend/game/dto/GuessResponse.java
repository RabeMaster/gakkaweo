package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GuessResponse(
    BigDecimal similarity,
    Integer attemptNumber,
    boolean isCorrect,
    String gameStatus,
    LocalDateTime timestamp) {}
