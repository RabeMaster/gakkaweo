package com.gakkaweo.backend.infra.notification;

import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dedup.NotificationDeduplicationCache;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ServerErrorNotifier {

  private static final int MESSAGE_MAX_LENGTH = 300;
  private static final int STACK_FRAME_MAX = 3;
  private static final int STACK_FRAME_MAX_LENGTH = 100;

  private final DiscordWebhookClient discordWebhookClient;
  private final NotificationProperties notificationProperties;
  private final NotificationDeduplicationCache deduplicationCache;
  private final Clock clock;

  public void notify(Exception ex, HttpServletRequest request) {
    try {
      NotificationProperties.ErrorAlert config = notificationProperties.getErrorAlert();
      if (!config.isEnabled()) {
        return;
      }

      String dedupKey = ex.getClass().getName() + ":" + request.getRequestURI();
      if (!deduplicationCache.shouldSend(dedupKey, config.getCooldown())) {
        return;
      }

      discordWebhookClient.send(NotificationLevel.HIGH, buildEmbed(ex, request));
    } catch (Exception notifyFailure) {
      log.warn("ERROR 알림 전송 중 예외 발생: {}", notifyFailure.getMessage(), notifyFailure);
    }
  }

  private DiscordEmbed buildEmbed(Exception ex, HttpServletRequest request) {
    List<DiscordEmbed.Field> fields = new ArrayList<>();
    fields.add(
        new DiscordEmbed.Field(
            "Request", request.getMethod() + " " + request.getRequestURI(), false));
    fields.add(new DiscordEmbed.Field("Exception", ex.getClass().getName(), false));
    fields.add(new DiscordEmbed.Field("Message", truncate(ex.getMessage()), false));
    fields.add(new DiscordEmbed.Field("Stack", summarizeStack(ex), false));
    return new DiscordEmbed("서버 오류 발생", null, null, fields, clock.instant().toString());
  }

  private String truncate(String value) {
    if (value == null) {
      return "-";
    }
    if (value.length() <= MESSAGE_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, MESSAGE_MAX_LENGTH) + "...";
  }

  private String summarizeStack(Throwable ex) {
    StackTraceElement[] frames = ex.getStackTrace();
    if (frames.length == 0) {
      return "(no stack)";
    }
    StringBuilder sb = new StringBuilder();
    int limit = Math.min(frames.length, STACK_FRAME_MAX);
    for (int i = 0; i < limit; i++) {
      String frame = frames[i].toString();
      if (frame.length() > STACK_FRAME_MAX_LENGTH) {
        frame = frame.substring(0, STACK_FRAME_MAX_LENGTH) + "...";
      }
      sb.append(frame);
      if (i < limit - 1) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }
}
