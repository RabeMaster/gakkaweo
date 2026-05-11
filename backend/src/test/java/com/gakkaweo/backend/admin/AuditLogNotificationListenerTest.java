package com.gakkaweo.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gakkaweo.backend.admin.event.AuditLogEvent;
import com.gakkaweo.backend.admin.event.AuditLogNotificationListener;
import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("AuditLogNotificationListener 단위 테스트")
class AuditLogNotificationListenerTest {

  private NotificationProperties props(boolean enabled) {
    return new NotificationProperties(
        new NotificationProperties.AuditAlert(enabled),
        new NotificationProperties.ErrorAlert(true, Duration.ofMinutes(5)));
  }

  private AuditLogEvent event(AuditAction action, String detail) {
    return new AuditLogEvent(
        action, action.targetType(), "user-1", "admin-nick", detail, "127.0.0.1", Instant.now());
  }

  @Test
  @DisplayName("enabled=true - 보안/파괴적 액션은 HIGH level로 Discord 전송 + timestamp 포함")
  void 활성화_전송() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(true));
    Instant createdAt = Instant.parse("2026-04-22T03:04:05Z");
    AuditLogEvent ev =
        new AuditLogEvent(
            AuditAction.USER_BAN,
            AuditAction.USER_BAN.targetType(),
            "user-1",
            "admin-nick",
            "계정 정지 사유",
            "127.0.0.1",
            createdAt);

    listener.onAuditLog(ev);

    ArgumentCaptor<DiscordEmbed> embedCaptor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(client).send(eq(NotificationLevel.HIGH), embedCaptor.capture());

    DiscordEmbed embed = embedCaptor.getValue();
    assertThat(embed.title()).isEqualTo("어드민 감사 액션");
    assertThat(embed.timestamp()).isEqualTo("2026-04-22T03:04:05Z");
    assertThat(embed.fields())
        .extracting(DiscordEmbed.Field::name)
        .contains("Action", "Admin", "Target", "IP", "Detail");
  }

  @Test
  @DisplayName("enabled=true - 루틴 관리 액션은 INFO level로 전송")
  void 루틴_액션_INFO() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(true));

    listener.onAuditLog(event(AuditAction.SENTENCE_CREATE, "문장 등록"));

    verify(client).send(eq(NotificationLevel.INFO), any(DiscordEmbed.class));
  }

  @Test
  @DisplayName("enabled=false - 전송 스킵")
  void 비활성화_스킵() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(false));

    listener.onAuditLog(event(AuditAction.USER_BAN, "detail"));

    verify(client, never()).send(any(), any());
  }

  @Test
  @DisplayName("detail 200자 초과 시 말줄임 처리")
  void detail_축약() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(true));

    String longDetail = "x".repeat(500);

    listener.onAuditLog(event(AuditAction.USER_BAN, longDetail));

    ArgumentCaptor<DiscordEmbed> embedCaptor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(client).send(eq(NotificationLevel.HIGH), embedCaptor.capture());

    String detailValue =
        embedCaptor.getValue().fields().stream()
            .filter(f -> f.name().equals("Detail"))
            .findFirst()
            .orElseThrow()
            .value();
    assertThat(detailValue).hasSize(203).endsWith("...");
  }

  @Test
  @DisplayName("targetId가 null/blank면 Target 필드에 타입만 표시")
  void target_id_없음() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(true));
    AuditLogEvent ev =
        new AuditLogEvent(
            AuditAction.RANKING_CACHE_RESET,
            AuditAction.RANKING_CACHE_RESET.targetType(),
            null,
            "admin-nick",
            null,
            "127.0.0.1",
            Instant.now());

    listener.onAuditLog(ev);

    ArgumentCaptor<DiscordEmbed> embedCaptor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(client).send(eq(NotificationLevel.HIGH), embedCaptor.capture());

    String targetValue =
        embedCaptor.getValue().fields().stream()
            .filter(f -> f.name().equals("Target"))
            .findFirst()
            .orElseThrow()
            .value();
    assertThat(targetValue).isEqualTo("SYSTEM");
  }

  @Test
  @DisplayName("detail이 비어있으면 Detail 필드 생략")
  void detail_빈값_생략() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    AuditLogNotificationListener listener = new AuditLogNotificationListener(client, props(true));

    listener.onAuditLog(event(AuditAction.USER_BAN, null));

    ArgumentCaptor<DiscordEmbed> embedCaptor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(client).send(eq(NotificationLevel.HIGH), embedCaptor.capture());

    assertThat(embedCaptor.getValue().fields())
        .extracting(DiscordEmbed.Field::name)
        .doesNotContain("Detail");
  }
}
