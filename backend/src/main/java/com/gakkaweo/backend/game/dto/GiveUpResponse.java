package com.gakkaweo.backend.game.dto;

import java.math.BigDecimal;

public record GiveUpResponse(
    String sentence, int attemptCount, BigDecimal bestSimilarity, String gameStatus) {}
