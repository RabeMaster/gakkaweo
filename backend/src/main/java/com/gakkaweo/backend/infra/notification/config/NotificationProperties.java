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

  @Getter
  @RequiredArgsConstructor
  public static class AuditAlert {
    private final boolean enabled;
  }

  @Getter
  @RequiredArgsConstructor
  public static class ErrorAlert {
    private final boolean enabled;
    private final Duration cooldown;
  }
}
