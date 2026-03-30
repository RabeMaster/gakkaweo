package com.gakkaweo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TodayWidgetResponse(
    UUID sentenceId,
    String sentence,
    long totalParticipants,
    long clearedCount,
    long inProgressCount,
    BigDecimal avgSimilarity,
    double avgAttemptCount,
    long unusedSentenceCount,
    int sseConnectionCount) {}
