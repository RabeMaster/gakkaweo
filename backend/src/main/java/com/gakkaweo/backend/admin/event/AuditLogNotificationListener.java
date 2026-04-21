package com.gakkaweo.backend.admin.event;

import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogNotificationListener {

  private static final int DETAIL_MAX_LENGTH = 200;

  private final DiscordWebhookClient discordWebhookClient;
  private final NotificationProperties notificationProperties;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAuditLog(AuditLogEvent event) {
    if (!notificationProperties.getAuditAlert().isEnabled()) {
      return;
    }

    try {
      discordWebhookClient.send(NotificationLevel.HIGH, buildEmbed(event));
    } catch (Exception e) {
      log.warn("어드민 감사 로그 Discord 알림 실패: {}", e.getMessage(), e);
    }
  }

  private DiscordEmbed buildEmbed(AuditLogEvent event) {
    List<DiscordEmbed.Field> fields = new ArrayList<>();
    fields.add(new DiscordEmbed.Field("Action", event.action(), true));
    fields.add(new DiscordEmbed.Field("Admin", safe(event.adminNickname()), true));
    fields.add(new DiscordEmbed.Field("Target", formatTarget(event), true));
    fields.add(new DiscordEmbed.Field("IP", safe(event.ipAddress()), true));
    if (event.detail() != null && !event.detail().isBlank()) {
      fields.add(new DiscordEmbed.Field("Detail", truncate(event.detail()), false));
    }
    return new DiscordEmbed("어드민 감사 액션", null, null, fields, event.createdAt().toString());
  }

  private String formatTarget(AuditLogEvent event) {
    String targetType = safe(event.targetType());
    String targetId = safe(event.targetId());
    if (targetId.isEmpty()) {
      return targetType;
    }
    return targetType + ":" + targetId;
  }

  private String safe(String value) {
    return value == null ? "-" : value;
  }

  private String truncate(String value) {
    if (value.length() <= DETAIL_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, DETAIL_MAX_LENGTH) + "...";
  }
}
