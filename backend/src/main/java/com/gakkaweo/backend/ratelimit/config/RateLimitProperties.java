package com.gakkaweo.backend.ratelimit.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@RequiredArgsConstructor
public class RateLimitProperties {

  private final int guessPerMinute;
  private final int readPerMinute;
  private final int ssePerMinute;
  private final int authPerMinute;
  private final int bucketExpiryMinutes;
}
