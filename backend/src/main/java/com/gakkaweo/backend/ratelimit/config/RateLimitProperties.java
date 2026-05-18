package com.gakkaweo.backend.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
    int guessPerMinute,
    int readPerMinute,
    int ssePerMinute,
    int authPerMinute,
    int adminPerMinute,
    int guessWsCapacity,
    int chatWsCapacity,
    int roomActionCapacity,
    int inviteWsCapacity,
    int guessWsIntervalSeconds,
    int chatWsIntervalSeconds,
    int roomActionIntervalSeconds,
    int inviteWsIntervalSeconds,
    int bucketExpiryMinutes) {}
