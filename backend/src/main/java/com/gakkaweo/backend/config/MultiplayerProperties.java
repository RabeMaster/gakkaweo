package com.gakkaweo.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.multi")
public record MultiplayerProperties(Timer timer, WebSocket webSocket, Room room) {

  public record Timer(@Min(1) int poolSize) {}

  public record WebSocket(
      int messageSizeLimit,
      int sendBufferSizeLimit,
      int sendTimeLimit,
      int heartbeatServer,
      int heartbeatClient) {}

  public record Room(@Min(1) int maxConcurrent, @Min(1) int idRetryCount) {}
}
