package com.gakkaweo.backend.ranking.sse.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.sse")
@Validated
public record SseProperties(@Min(1) int maxConnections) {}
