package com.gakkaweo.backend.infra.notification.dedup;

import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeduplicationCache {

  private final Clock clock;
  private final NotificationProperties notificationProperties;
  private final Map<String, Instant> lastSentAt = new ConcurrentHashMap<>();

  public boolean shouldSend(String key, Duration cooldown) {
    Instant now = clock.instant();
    AtomicBoolean allowed = new AtomicBoolean(false);
    lastSentAt.compute(
        key,
        (k, previous) -> {
          if (previous == null || Duration.between(previous, now).compareTo(cooldown) >= 0) {
            allowed.set(true);
            return now;
          }
          return previous;
        });
    return allowed.get();
  }

  @Scheduled(fixedDelayString = "${app.notification.dedup.cleanup-interval-ms:300000}")
  public void cleanup() {
    Duration retention = notificationProperties.errorAlert().cooldown().multipliedBy(2);
    Instant expiry = clock.instant().minus(retention);
    int before = lastSentAt.size();
    lastSentAt.entrySet().removeIf(entry -> entry.getValue().isBefore(expiry));
    int removed = before - lastSentAt.size();
    if (removed > 0) {
      log.debug("알림 중복 억제 캐시 정리: {}개 제거, 남은 엔트리 {}개", removed, lastSentAt.size());
    }
  }
}
