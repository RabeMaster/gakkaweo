package com.gakkaweo.backend.infra.notification.config;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.discord")
@Getter
@RequiredArgsConstructor
public class DiscordWebhookProperties {

  private final String webhookUrl;
  private final Duration timeout;
  private final String mentionRoleId;
}
