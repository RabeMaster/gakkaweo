package com.gakkaweo.backend.infra.notification.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
@Getter
@RequiredArgsConstructor
public class NotificationProperties {

  private final AuditAlert auditAlert;
  private final ErrorAlert errorAlert;

  public record AuditAlert(boolean enabled) {}

  public record ErrorAlert(boolean enabled, Duration cooldown) {}
}
