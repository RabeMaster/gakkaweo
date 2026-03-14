package com.gakkaweo.backend.infra.ai.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-service")
@Getter
@RequiredArgsConstructor
public class AiServiceProperties {

  private final String url;
  private final Duration timeout;
  private final Duration similarityCacheTtl;
}
