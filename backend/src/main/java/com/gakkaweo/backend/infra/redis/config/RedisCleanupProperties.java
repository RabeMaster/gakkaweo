package com.gakkaweo.backend.infra.redis.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.redis.cleanup")
@Getter
@RequiredArgsConstructor
public class RedisCleanupProperties {

  private final boolean enabled;
  private final int scanBatchSize;
  private final int purgeOlderThanDays;
  private final boolean notifyOnZero;
}
