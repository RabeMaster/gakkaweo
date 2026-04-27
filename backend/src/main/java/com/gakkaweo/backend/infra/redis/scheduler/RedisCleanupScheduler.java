package com.gakkaweo.backend.infra.redis.scheduler;

import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.gakkaweo.backend.infra.redis.config.RedisCleanupProperties;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisCleanupScheduler {

  private static final int COLOR_INFO = 0x3498DB;
  private static final int COLOR_PARTIAL = 0xFEE75C;

  private final StringRedisTemplate redisTemplate;
  private final RetryRegistry retryRegistry;
  private final DiscordWebhookClient discordWebhookClient;
  private final RedisCleanupProperties properties;
  private final Clock clock;

  @Scheduled(cron = "0 30 4,10,16,22 * * *", zone = "Asia/Seoul")
  public void executeCleanup() {
    if (!properties.isEnabled()) {
      log.debug("Redis 정리 스케줄러 비활성화");
      return;
    }

    log.info("Redis 정리 스케줄러 시작");

    Retry orphanRetry = retryRegistry.retry("redisCleanupOrphanScan");
    Retry purgeRetry = retryRegistry.retry("redisCleanupRankingPurge");

    int[] orphanCount = {0};
    int[] purgedRanking = {0};
    int[] purgedDetail = {0};
    boolean orphanFailed = false;
    boolean purgeFailed = false;

    try {
      orphanRetry.executeRunnable(() -> orphanCount[0] = scanOrphanKeys());
    } catch (Exception e) {
      orphanFailed = true;
      log.error("Redis orphan 스캔 실패: {}", e.getMessage(), e);
    }

    LocalDate cutoff = LocalDate.now(clock).minusDays(properties.getPurgeOlderThanDays());
    try {
      purgeRetry.executeRunnable(
          () -> {
            purgedRanking[0] =
                purgeStaleKeys(
                    RedisKeyConstants.rankingScanPattern(),
                    RedisKeyConstants::extractDateFromRankingKey,
                    cutoff);
            purgedDetail[0] =
                purgeStaleKeys(
                    RedisKeyConstants.rankingDetailScanPattern(),
                    RedisKeyConstants::extractDateFromDetailKey,
                    cutoff);
          });
    } catch (Exception e) {
      purgeFailed = true;
      log.error("Redis ranking 누수 정리 실패: {}", e.getMessage(), e);
    }

    RedisCleanupReport report =
        new RedisCleanupReport(
            orphanCount[0], purgedRanking[0], purgedDetail[0], orphanFailed, purgeFailed);

    log.info(
        "Redis 정리 스케줄러 완료: orphan={}, purgedRanking={}, purgedDetail={}, failures=[{}{}]",
        report.orphanCount(),
        report.purgedRankingCount(),
        report.purgedDetailCount(),
        report.orphanScanFailed() ? "orphanScan " : "",
        report.rankingPurgeFailed() ? "rankingPurge" : "");

    notifyDiscord(report);
  }

  private int scanOrphanKeys() {
    Set<String> known = RedisKeyConstants.knownPrefixes();
    int count = 0;
    try (Cursor<String> cursor = openCursor("*")) {
      while (cursor.hasNext()) {
        String key = cursor.next();
        if (!matchesAnyPrefix(key, known)) {
          log.warn("Redis orphan 키 발견: {}", key);
          count++;
        }
      }
    }
    return count;
  }

  private int purgeStaleKeys(
      String pattern, Function<String, LocalDate> dateExtractor, LocalDate cutoff) {
    List<String> targets = new ArrayList<>();
    try (Cursor<String> cursor = openCursor(pattern)) {
      while (cursor.hasNext()) {
        String key = cursor.next();
        LocalDate keyDate = dateExtractor.apply(key);
        if (keyDate == null) {
          log.warn("Redis 키 날짜 파싱 실패 (orphan으로 분류 권장): {}", key);
          continue;
        }
        if (!keyDate.isAfter(cutoff)) {
          targets.add(key);
        }
      }
    }
    if (targets.isEmpty()) {
      return 0;
    }
    Long removed = redisTemplate.delete(targets);
    int count = removed == null ? 0 : removed.intValue();
    log.info("Redis 누수 키 삭제: pattern={}, count={}", pattern, count);
    return count;
  }

  private Cursor<String> openCursor(String pattern) {
    ScanOptions options =
        ScanOptions.scanOptions().match(pattern).count(properties.getScanBatchSize()).build();
    return redisTemplate.scan(options);
  }

  private boolean matchesAnyPrefix(String key, Set<String> prefixes) {
    for (String prefix : prefixes) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private void notifyDiscord(RedisCleanupReport report) {
    if (!report.hasAnyAction() && !report.hasFailure() && !properties.isNotifyOnZero()) {
      return;
    }
    try {
      NotificationLevel level =
          report.hasFailure() ? NotificationLevel.HIGH : NotificationLevel.INFO;
      discordWebhookClient.send(level, buildEmbed(report));
    } catch (Exception e) {
      log.warn("Discord 웹훅 전송 중 예외 발생: {}", e.getMessage(), e);
    }
  }

  private DiscordEmbed buildEmbed(RedisCleanupReport report) {
    String title;
    int color;
    if (report.hasFailure()) {
      title = "Redis 정리 스케줄러 일부 실패";
      color = COLOR_PARTIAL;
    } else {
      title = "Redis 정리 스케줄러 실행 완료";
      color = COLOR_INFO;
    }

    String description = report.hasAnyAction() ? "정리 대상이 감지되어 처리되었습니다." : "이번 사이클에서 정리할 키가 없습니다.";

    List<DiscordEmbed.Field> fields =
        List.of(
            new DiscordEmbed.Field("Orphan 키", String.valueOf(report.orphanCount()), true),
            new DiscordEmbed.Field("정리 ranking", String.valueOf(report.purgedRankingCount()), true),
            new DiscordEmbed.Field("정리 detail", String.valueOf(report.purgedDetailCount()), true),
            new DiscordEmbed.Field("Orphan 스캔", report.orphanScanFailed() ? "실패" : "성공", true),
            new DiscordEmbed.Field("Ranking 정리", report.rankingPurgeFailed() ? "실패" : "성공", true),
            new DiscordEmbed.Field(
                "삭제 임계 일수", String.valueOf(properties.getPurgeOlderThanDays()), true));

    return new DiscordEmbed(title, description, color, fields);
  }
}
