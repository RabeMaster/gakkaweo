package com.gakkaweo.backend.infra.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.discord")
public record DiscordWebhookProperties(String webhookUrl, Duration timeout, String mentionRoleId) {}
