package com.gakkaweo.backend.admin.dto;

import java.math.BigDecimal;

public record SentenceStatsResponse(
    long totalSessions,
    long clearedSessions,
    double clearRate,
    BigDecimal avgSimilarity,
    double avgAttemptCount) {}
