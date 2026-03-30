package com.gakkaweo.backend.admin.dto;

public record SystemStatusResponse(
    int sseConnectionCount,
    boolean aiServiceHealthy,
    long aiServiceResponseMs,
    boolean redisHealthy,
    long totalMembers,
    long totalSentences,
    long unusedSentences) {}
