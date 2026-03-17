package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GameStatusResponse(
    UUID sentenceId,
    String gameStatus,
    BigDecimal bestSimilarity,
    int attemptCount,
    Instant clearedAt) {}
