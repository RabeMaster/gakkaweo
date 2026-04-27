package com.gakkaweo.backend.infra.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(AuditAlert auditAlert, ErrorAlert errorAlert) {

  public record AuditAlert(boolean enabled) {}

  public record ErrorAlert(boolean enabled, Duration cooldown) {}
}
