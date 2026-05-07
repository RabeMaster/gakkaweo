package com.gakkaweo.backend.infra.redis.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.redis.cleanup")
@Validated
public record RedisCleanupProperties(
    boolean enabled,
    @Min(1) int scanBatchSize,
    @Min(2) int purgeOlderThanDays,
    boolean notifyOnZero) {}
