package com.gakkaweo.backend.infra.redis.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.redis.cleanup")
@Validated
@Getter
@RequiredArgsConstructor
public class RedisCleanupProperties {

  private final boolean enabled;

  @Min(1)
  private final int scanBatchSize;

  @Min(2)
  private final int purgeOlderThanDays;

  private final boolean notifyOnZero;
}
