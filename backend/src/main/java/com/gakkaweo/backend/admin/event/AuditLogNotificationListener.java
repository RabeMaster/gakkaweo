package com.gakkaweo.backend.admin.event;

import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuditLogNotificationListener {

  private static final int DETAIL_MAX_LENGTH = 200;

  private final DiscordWebhookClient discordWebhookClient;
  private final NotificationProperties notificationProperties;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAuditLog(AuditLogEvent event) {
    if (!notificationProperties.getAuditAlert().enabled()) {
      return;
    }

    discordWebhookClient.send(NotificationLevel.HIGH, buildEmbed(event));
  }

  private DiscordEmbed buildEmbed(AuditLogEvent event) {
    List<DiscordEmbed.Field> fields = new ArrayList<>();
    fields.add(new DiscordEmbed.Field("Action", event.action().name(), true));
    fields.add(new DiscordEmbed.Field("Admin", safe(event.adminNickname()), true));
    fields.add(new DiscordEmbed.Field("Target", formatTarget(event), true));
    fields.add(new DiscordEmbed.Field("IP", safe(event.ipAddress()), true));
    if (event.detail() != null && !event.detail().isBlank()) {
      fields.add(new DiscordEmbed.Field("Detail", truncate(event.detail()), false));
    }
    return new DiscordEmbed("어드민 감사 액션", null, null, fields, event.createdAt().toString());
  }

  private String formatTarget(AuditLogEvent event) {
    String targetType = event.targetType().name();
    String targetId = event.targetId();
    if (targetId == null || targetId.isBlank()) {
      return targetType;
    }
    return targetType + ":" + targetId;
  }

  private String safe(String value) {
    return value == null ? "-" : value;
  }

  private String truncate(String value) {
    if (value == null) {
      return "-";
    }
    if (value.length() <= DETAIL_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, DETAIL_MAX_LENGTH) + "...";
  }
}
