package com.gakkaweo.backend.ranking.sse.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.sse")
@Validated
@Getter
@RequiredArgsConstructor
public class SseProperties {

  @Min(1)
  private final int maxConnections;
}
