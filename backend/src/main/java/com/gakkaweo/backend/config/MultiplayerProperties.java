package com.gakkaweo.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.multi")
public record MultiplayerProperties(Timer timer, WebSocket webSocket) {

  public record Timer(@Min(1) int poolSize) {}

  public record WebSocket(
      int messageSizeLimit,
      int sendBufferSizeLimit,
      int sendTimeLimit,
      int heartbeatServer,
      int heartbeatClient) {}
}
