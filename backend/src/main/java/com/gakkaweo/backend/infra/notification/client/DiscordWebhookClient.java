package com.gakkaweo.backend.infra.notification.client;

import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.config.DiscordWebhookProperties;
import com.gakkaweo.backend.infra.notification.dto.AllowedMentions;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.gakkaweo.backend.infra.notification.dto.DiscordWebhookPayload;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
@RequiredArgsConstructor
public class DiscordWebhookClient {

  private static final int COLOR_HIGH = 0xE67E22;
  private static final int COLOR_INFO = 0x3498DB;

  private final RestClient discordWebhookRestClient;
  private final DiscordWebhookProperties properties;
  private final MeterRegistry meterRegistry;

  private Counter webhookSuccessCounter;
  private Counter webhookFailureCounter;

  @PostConstruct
  void initCounters() {
    webhookSuccessCounter =
        Counter.builder("discord.webhook.total")
            .tag("result", "success")
            .description("Total Discord webhook dispatches")
            .register(meterRegistry);
    webhookFailureCounter =
        Counter.builder("discord.webhook.total")
            .tag("result", "failure")
            .description("Total Discord webhook dispatches")
            .register(meterRegistry);
  }

  @Async("discordWebhookExecutor")
  public void send(NotificationLevel level, DiscordEmbed embed) {
    DiscordEmbed finalEmbed = embed.color() != null ? embed : withLevelColor(embed, level);
    DiscordWebhookPayload payload =
        new DiscordWebhookPayload(
            buildContent(level), List.of(finalEmbed), buildAllowedMentions(level));
    dispatch(payload);
  }

  private void dispatch(DiscordWebhookPayload payload) {
    String webhookUrl = properties.webhookUrl();
    if (!StringUtils.hasText(webhookUrl)) {
      log.debug("Discord 웹훅 URL 미설정, 전송 건너뜀");
      return;
    }

    try {
      discordWebhookRestClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .toBodilessEntity();
      webhookSuccessCounter.increment();
    } catch (RestClientException e) {
      webhookFailureCounter.increment();
      log.warn("Discord 웹훅 전송 실패: {}", e.getMessage(), e);
    }
  }

  private String buildContent(NotificationLevel level) {
    return switch (level) {
      case HIGH -> {
        String roleId = properties.mentionRoleId();
        yield StringUtils.hasText(roleId) ? "<@&" + roleId + ">" : "";
      }
      case INFO -> "";
    };
  }

  private AllowedMentions buildAllowedMentions(NotificationLevel level) {
    return switch (level) {
      case HIGH -> {
        String roleId = properties.mentionRoleId();
        yield StringUtils.hasText(roleId)
            ? AllowedMentions.roles(List.of(roleId))
            : AllowedMentions.none();
      }
      case INFO -> AllowedMentions.none();
    };
  }

  private DiscordEmbed withLevelColor(DiscordEmbed embed, NotificationLevel level) {
    int color =
        switch (level) {
          case HIGH -> COLOR_HIGH;
          case INFO -> COLOR_INFO;
        };
    return new DiscordEmbed(
        embed.title(), embed.description(), color, embed.fields(), embed.timestamp());
  }
}
