package com.gakkaweo.backend.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.infra.notification.config.NotificationProperties;
import com.gakkaweo.backend.infra.notification.dedup.NotificationDeduplicationCache;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NotificationDeduplicationCache 단위 테스트")
class NotificationDeduplicationCacheTest {

  private static class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      this.now = this.now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private NotificationProperties propsWithCooldown(Duration cooldown) {
    return new NotificationProperties(
        new NotificationProperties.AuditAlert(true),
        new NotificationProperties.ErrorAlert(true, cooldown));
  }

  @Test
  @DisplayName("첫 호출은 전송 허용")
  void 첫_호출_허용() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    NotificationDeduplicationCache cache =
        new NotificationDeduplicationCache(clock, propsWithCooldown(Duration.ofMinutes(5)));

    assertThat(cache.shouldSend("key", Duration.ofMinutes(5))).isTrue();
  }

  @Test
  @DisplayName("쿨다운 내 재호출은 차단")
  void 쿨다운_내_차단() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    NotificationDeduplicationCache cache =
        new NotificationDeduplicationCache(clock, propsWithCooldown(Duration.ofMinutes(5)));

    cache.shouldSend("key", Duration.ofMinutes(5));
    clock.advance(Duration.ofMinutes(4));

    assertThat(cache.shouldSend("key", Duration.ofMinutes(5))).isFalse();
  }

  @Test
  @DisplayName("쿨다운 경과 후 재호출은 허용")
  void 쿨다운_경과_허용() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    NotificationDeduplicationCache cache =
        new NotificationDeduplicationCache(clock, propsWithCooldown(Duration.ofMinutes(5)));

    cache.shouldSend("key", Duration.ofMinutes(5));
    clock.advance(Duration.ofMinutes(5));

    assertThat(cache.shouldSend("key", Duration.ofMinutes(5))).isTrue();
  }

  @Test
  @DisplayName("서로 다른 key는 독립적")
  void key_독립성() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    NotificationDeduplicationCache cache =
        new NotificationDeduplicationCache(clock, propsWithCooldown(Duration.ofMinutes(5)));

    assertThat(cache.shouldSend("A", Duration.ofMinutes(5))).isTrue();
    assertThat(cache.shouldSend("B", Duration.ofMinutes(5))).isTrue();
  }

  @Test
  @DisplayName("cleanup - cooldown 2배 경과 엔트리 제거")
  void cleanup_오래된_엔트리_제거() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    NotificationDeduplicationCache cache =
        new NotificationDeduplicationCache(clock, propsWithCooldown(Duration.ofMinutes(5)));

    cache.shouldSend("stale", Duration.ofMinutes(5));
    clock.advance(Duration.ofMinutes(11));
    cache.shouldSend("fresh", Duration.ofMinutes(5));

    cache.cleanup();

    assertThat(cache.shouldSend("stale", Duration.ofMinutes(5))).isTrue();
    assertThat(cache.shouldSend("fresh", Duration.ofMinutes(5))).isFalse();
  }
}
