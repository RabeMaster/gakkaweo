package com.gakkaweo.backend.infra.notification.client;

import com.gakkaweo.backend.infra.notification.config.DiscordWebhookProperties;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class DiscordWebhookClient {

  private final RestClient discordWebhookRestClient;
  private final String webhookUrl;

  public DiscordWebhookClient(
      RestClient discordWebhookRestClient, DiscordWebhookProperties properties) {
    this.discordWebhookRestClient = discordWebhookRestClient;
    this.webhookUrl = properties.getWebhookUrl();
  }

  @Async("discordWebhookExecutor")
  public void sendEmbed(DiscordEmbed embed) {
    if (!StringUtils.hasText(webhookUrl)) {
      log.debug("Discord 웹훅 URL 미설정, 전송 건너뜀");
      return;
    }

    try {
      discordWebhookRestClient
          .post()
          .uri(webhookUrl)
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("embeds", List.of(embed)))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("Discord 웹훅 전송 실패: {}", e.getMessage(), e);
    }
  }
}
