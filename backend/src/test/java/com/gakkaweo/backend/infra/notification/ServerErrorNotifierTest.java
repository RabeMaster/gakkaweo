package com.gakkaweo.backend.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dedup.NotificationDeduplicationCache;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("ServerErrorNotifier 단위 테스트")
class ServerErrorNotifierTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-22T10:30:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  private NotificationProperties props(boolean enabled) {
    return new NotificationProperties(
        new NotificationProperties.AuditAlert(false),
        new NotificationProperties.ErrorAlert(enabled, Duration.ofMinutes(5)));
  }

  @Test
  @DisplayName("errorAlert.enabled=false - Discord 전송 없음")
  void 비활성화_전송_없음() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    NotificationDeduplicationCache dedup = mock(NotificationDeduplicationCache.class);
    ServerErrorNotifier notifier =
        new ServerErrorNotifier(client, props(false), dedup, FIXED_CLOCK);

    notifier.notify(new RuntimeException("boom"), new MockHttpServletRequest("GET", "/boom"));

    verify(client, never()).send(any(), any());
  }

  @Test
  @DisplayName("errorAlert 활성 + dedup shouldSend=true - HIGH level 전송")
  void 활성화_전송() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    NotificationDeduplicationCache dedup = mock(NotificationDeduplicationCache.class);
    when(dedup.shouldSend(anyString(), any())).thenReturn(true);
    ServerErrorNotifier notifier = new ServerErrorNotifier(client, props(true), dedup, FIXED_CLOCK);

    notifier.notify(new RuntimeException("boom"), new MockHttpServletRequest("POST", "/api/hello"));

    verify(client).send(eq(NotificationLevel.HIGH), any(DiscordEmbed.class));
  }

  @Test
  @DisplayName("dedup shouldSend=false - 쿨다운 내 전송 스킵")
  void 쿨다운_내_스킵() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    NotificationDeduplicationCache dedup = mock(NotificationDeduplicationCache.class);
    when(dedup.shouldSend(anyString(), any())).thenReturn(false);
    ServerErrorNotifier notifier = new ServerErrorNotifier(client, props(true), dedup, FIXED_CLOCK);

    notifier.notify(new RuntimeException("boom"), new MockHttpServletRequest("GET", "/boom"));

    verify(client, never()).send(any(), any());
  }

  @Test
  @DisplayName("Discord 전송 중 예외 발생해도 호출자로 전파되지 않음")
  void Discord_예외_흡수() {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    NotificationDeduplicationCache dedup = mock(NotificationDeduplicationCache.class);
    when(dedup.shouldSend(anyString(), any())).thenThrow(new RuntimeException("dedup failure"));
    ServerErrorNotifier notifier = new ServerErrorNotifier(client, props(true), dedup, FIXED_CLOCK);

    notifier.notify(new RuntimeException("boom"), new MockHttpServletRequest("GET", "/boom"));

    verify(client, never()).send(any(), any());
  }

  @Test
  @DisplayName("embed 필드 구성 - Request/Exception/Message/Stack 4개 필드 + timestamp")
  void embed_필드_구성() {
    DiscordEmbed embed = captureEmbed(new RuntimeException("boom"), "POST", "/api/hello");

    assertThat(embed.title()).isEqualTo("서버 오류 발생");
    assertThat(embed.timestamp()).isEqualTo(FIXED_INSTANT.toString());
    assertThat(embed.fields())
        .extracting(DiscordEmbed.Field::name)
        .containsExactly("Request", "Exception", "Message", "Stack");
    assertThat(fieldValue(embed, "Request")).isEqualTo("POST /api/hello");
    assertThat(fieldValue(embed, "Exception")).isEqualTo(RuntimeException.class.getName());
    assertThat(fieldValue(embed, "Message")).isEqualTo("boom");
  }

  @Test
  @DisplayName("Message - null 메시지는 \"-\"로 대체")
  void message_null_처리() {
    DiscordEmbed embed = captureEmbed(new RuntimeException((String) null), "GET", "/x");

    assertThat(fieldValue(embed, "Message")).isEqualTo("-");
  }

  @Test
  @DisplayName("Message - 300자 초과 시 300자 + \"...\"로 축약")
  void message_축약() {
    String longMessage = "x".repeat(500);
    DiscordEmbed embed = captureEmbed(new RuntimeException(longMessage), "GET", "/x");

    String value = fieldValue(embed, "Message");
    assertThat(value).hasSize(303).startsWith("x".repeat(300)).endsWith("...");
  }

  @Test
  @DisplayName("Stack - 최상위 3프레임만 포함, 각 프레임 100자 상한")
  void stack_요약() {
    Exception ex = new RuntimeException("boom");
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.example.A", "m1", "A.java", 10),
          new StackTraceElement("com.example.B", "m2", "B.java", 20),
          new StackTraceElement("com.example.C", "m3", "C.java", 30),
          new StackTraceElement("com.example.D", "m4", "D.java", 40),
        });

    DiscordEmbed embed = captureEmbed(ex, "GET", "/x");

    String stack = fieldValue(embed, "Stack");
    String[] lines = stack.split("\n");
    assertThat(lines).hasSize(3);
    assertThat(lines[0]).contains("com.example.A.m1");
    assertThat(lines[2]).contains("com.example.C.m3");
    assertThat(stack).doesNotContain("com.example.D");
  }

  @Test
  @DisplayName("Stack - 빈 스택트레이스면 \"(no stack)\"")
  void stack_empty() {
    Exception ex = new RuntimeException("boom");
    ex.setStackTrace(new StackTraceElement[0]);

    DiscordEmbed embed = captureEmbed(ex, "GET", "/x");

    assertThat(fieldValue(embed, "Stack")).isEqualTo("(no stack)");
  }

  @Test
  @DisplayName("Stack - 각 프레임 100자 초과 시 말줄임")
  void stack_프레임_축약() {
    String longClass = "com.example." + "A".repeat(200);
    Exception ex = new RuntimeException("boom");
    ex.setStackTrace(new StackTraceElement[] {new StackTraceElement(longClass, "m", "X.java", 1)});

    DiscordEmbed embed = captureEmbed(ex, "GET", "/x");

    assertThat(fieldValue(embed, "Stack")).hasSize(103).endsWith("...");
  }

  private DiscordEmbed captureEmbed(Exception ex, String method, String uri) {
    DiscordWebhookClient client = mock(DiscordWebhookClient.class);
    NotificationDeduplicationCache dedup = mock(NotificationDeduplicationCache.class);
    when(dedup.shouldSend(anyString(), any())).thenReturn(true);
    ServerErrorNotifier notifier = new ServerErrorNotifier(client, props(true), dedup, FIXED_CLOCK);

    notifier.notify(ex, new MockHttpServletRequest(method, uri));

    ArgumentCaptor<DiscordEmbed> captor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(client).send(eq(NotificationLevel.HIGH), captor.capture());
    return captor.getValue();
  }

  private String fieldValue(DiscordEmbed embed, String name) {
    return embed.fields().stream()
        .filter(f -> f.name().equals(name))
        .findFirst()
        .orElseThrow()
        .value();
  }
}
