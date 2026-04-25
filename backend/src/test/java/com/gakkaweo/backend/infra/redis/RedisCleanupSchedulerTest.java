package com.gakkaweo.backend.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.gakkaweo.backend.infra.redis.scheduler.RedisCleanupScheduler;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("RedisCleanupScheduler 통합 테스트")
class RedisCleanupSchedulerTest extends IntegrationTestBase {

  @Autowired RedisCleanupScheduler scheduler;
  @Autowired StringRedisTemplate redisTemplate;

  @MockitoBean DiscordWebhookClient discordWebhookClient;

  @Test
  @DisplayName("executeCleanup - D-2 이상 키 정리 + orphan 카운트 + Discord INFO 발송")
  void 정리_시나리오() {
    LocalDate today = LocalDate.now(clock);
    LocalDate dMinus3 = today.minusDays(3);
    LocalDate dMinus2 = today.minusDays(2);
    LocalDate yesterday = today.minusDays(1);

    redisTemplate.opsForValue().set("ranking:" + dMinus3, "1");
    redisTemplate.opsForHash().put("ranking_detail:" + dMinus3 + ":member:abc", "k", "v");
    redisTemplate.opsForValue().set("ranking:" + dMinus2, "1");
    redisTemplate.opsForHash().put("ranking_detail:" + dMinus2 + ":member:def", "k", "v");
    redisTemplate.opsForValue().set("ranking:" + yesterday, "1");
    redisTemplate.opsForValue().set("ranking:" + today, "1");
    redisTemplate.opsForValue().set("unknown_prefix:something", "1");

    scheduler.executeCleanup();

    assertThat(redisTemplate.hasKey("ranking:" + dMinus3)).isFalse();
    assertThat(redisTemplate.hasKey("ranking_detail:" + dMinus3 + ":member:abc")).isFalse();
    assertThat(redisTemplate.hasKey("ranking:" + dMinus2)).isFalse();
    assertThat(redisTemplate.hasKey("ranking_detail:" + dMinus2 + ":member:def")).isFalse();
    assertThat(redisTemplate.hasKey("ranking:" + yesterday)).isTrue();
    assertThat(redisTemplate.hasKey("ranking:" + today)).isTrue();
    assertThat(redisTemplate.hasKey("unknown_prefix:something")).isTrue();

    ArgumentCaptor<DiscordEmbed> captor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(discordWebhookClient).send(eq(NotificationLevel.INFO), captor.capture());
    DiscordEmbed embed = captor.getValue();
    assertThat(embed.title()).contains("실행 완료");
    assertThat(embed.fields())
        .anyMatch(f -> f.name().equals("Orphan 키") && f.value().equals("1"))
        .anyMatch(f -> f.name().equals("정리 ranking") && f.value().equals("2"))
        .anyMatch(f -> f.name().equals("정리 detail") && f.value().equals("2"));
  }

  @Test
  @DisplayName("executeCleanup - 정리 0건 + notifyOnZero=false이면 Discord 미발송")
  void 정리_0건_미발송() {
    scheduler.executeCleanup();

    verifyNoInteractions(discordWebhookClient);
  }

  @Test
  @DisplayName("executeCleanup - 잘못된 키 형식은 스킵하고 보존 (parse 실패는 orphan/purge 모두 미해당)")
  void 잘못된_키_형식() {
    redisTemplate.opsForValue().set("ranking:not-a-date", "1");
    redisTemplate.opsForValue().set("ranking_detail:also-bad:member:abc", "v");

    scheduler.executeCleanup();

    assertThat(redisTemplate.hasKey("ranking:not-a-date")).isTrue();
    assertThat(redisTemplate.hasKey("ranking_detail:also-bad:member:abc")).isTrue();
    verifyNoInteractions(discordWebhookClient);
  }

  @Test
  @DisplayName("executeCleanup - 화이트리스트 외 prefix는 orphan으로 분류만, 삭제 X")
  void orphan_분류만() {
    redisTemplate.opsForValue().set("strange_prefix:value", "1");
    redisTemplate.opsForValue().set("malicious:foo", "1");

    scheduler.executeCleanup();

    assertThat(redisTemplate.hasKey("strange_prefix:value")).isTrue();
    assertThat(redisTemplate.hasKey("malicious:foo")).isTrue();

    ArgumentCaptor<DiscordEmbed> captor = ArgumentCaptor.forClass(DiscordEmbed.class);
    verify(discordWebhookClient).send(eq(NotificationLevel.INFO), captor.capture());
    assertThat(captor.getValue().fields())
        .anyMatch(f -> f.name().equals("Orphan 키") && f.value().equals("2"));
  }
}
