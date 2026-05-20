package com.gakkaweo.backend.game.scheduler;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.infra.notification.NotificationLevel;
import com.gakkaweo.backend.infra.notification.client.DiscordWebhookClient;
import com.gakkaweo.backend.infra.notification.dto.DiscordEmbed;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.annotation.Timed;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class DailySentenceScheduler {

  private static final int COLOR_SUCCESS = 0x57F287;
  private static final int COLOR_PARTIAL = 0xFEE75C;
  private static final int COLOR_FAILURE = 0xED4245;

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final RankingService rankingService;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final RetryRegistry retryRegistry;
  private final DiscordWebhookClient discordWebhookClient;
  private final Clock clock;

  private static String statusLabel(boolean succeeded) {
    return succeeded ? "성공" : "실패";
  }

  @Timed(value = "scheduler.midnight.duration", description = "Midnight scheduler execution time")
  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void executeMidnightJob() {
    executeMidnightJob(true);
  }

  void executeMidnightJob(boolean notifyOnDiscord) {
    log.info("일일 스케줄러 시작");
    LocalDate today = LocalDate.now(clock);
    LocalDate yesterday = today.minusDays(1);

    Retry expireRetry = retryRegistry.retry("schedulerExpire");
    Retry snapshotRetry = retryRegistry.retry("schedulerSnapshot");
    Retry expireKeysRetry = retryRegistry.retry("schedulerExpireKeys");
    Retry selectTodayRetry = retryRegistry.retry("schedulerSelectToday");

    boolean expireSucceeded = false;
    boolean snapshotSucceeded = false;
    boolean keyExpireSucceeded = false;
    boolean selectSucceeded = false;
    int yesterdayTotalPlayers = 0;
    RankingSnapshot[] snapshotHolder = new RankingSnapshot[1];

    try {
      expireRetry.executeRunnable(
          () ->
              transactionTemplate.executeWithoutResult(
                  status -> expireYesterdaySessions(yesterday)));
      expireSucceeded = true;
    } catch (Exception e) {
      log.error("전날 세션 만료 처리 실패: {}", e.getMessage(), e);
    }

    try {
      snapshotRetry.executeRunnable(
          () -> {
            RankingSnapshot snapshot = rankingService.getAllRankingsForDate(yesterday);
            snapshotHolder[0] = snapshot;
            transactionTemplate.executeWithoutResult(
                status -> saveRankingSnapshot(yesterday, snapshot));
          });
      snapshotSucceeded = true;
      yesterdayTotalPlayers = snapshotHolder[0].totalPlayers();
    } catch (Exception e) {
      log.error("전날 랭킹 스냅샷 저장 실패: {}", e.getMessage(), e);
    }

    if (snapshotSucceeded) {
      try {
        expireKeysRetry.executeRunnable(
            () -> rankingService.expirePreviousDayRankingKeys(yesterday));
        keyExpireSucceeded = true;
      } catch (Exception e) {
        log.error("전날 랭킹 키 만료 처리 실패: {}", e.getMessage(), e);
      }
    }

    try {
      selectTodayRetry.executeRunnable(
          () -> transactionTemplate.executeWithoutResult(status -> selectTodaySentence(today)));
      selectSucceeded = true;
    } catch (Exception e) {
      log.error("오늘의 문장 선정 실패: {}", e.getMessage(), e);
    }

    Optional<DailySentence> todayOpt = dailySentenceRepository.findByUsedAt(today);
    String todaySentence = todayOpt.map(DailySentence::getSentence).orElse(null);
    todayOpt.ifPresent(s -> eventPublisher.publishEvent(new DayChangeEvent(s.getPublicId())));

    long unusedCount = dailySentenceRepository.countUnusedActive();

    MidnightJobReport report =
        new MidnightJobReport(
            expireSucceeded,
            snapshotSucceeded,
            keyExpireSucceeded,
            selectSucceeded,
            todaySentence,
            yesterdayTotalPlayers,
            unusedCount);

    if (notifyOnDiscord) {
      notifyDiscord(report);
    }

    log.info("일일 스케줄러 완료");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    LocalDate today = LocalDate.now(clock);
    if (dailySentenceRepository.findByUsedAt(today).isEmpty()) {
      log.info("오늘 날짜 기준으로 선택된 문장이 없어 즉시 선정을 진행합니다.");
      executeMidnightJob(false);
    }
  }

  private void expireYesterdaySessions(LocalDate yesterday) {
    dailySentenceRepository
        .findByUsedAt(yesterday)
        .ifPresent(
            sentence -> {
              int count = gameSessionRepository.expireInProgressSessions(sentence);
              if (count > 0) {
                log.info("전날 세션 만료 처리: date={}, count={}", yesterday, count);
              }
            });
  }

  private void saveRankingSnapshot(LocalDate yesterday, RankingSnapshot snapshot) {
    if (snapshot.memberRanks().isEmpty()) {
      log.info("전날 랭킹 스냅샷: 데이터 없음, date={}", yesterday);
      return;
    }

    DailySentence sentence = dailySentenceRepository.findByUsedAt(yesterday).orElse(null);
    if (sentence == null) {
      log.warn("전날 문장 미발견, 랭킹 스냅샷 건너뜀: date={}", yesterday);
      return;
    }

    sentence.recordTotalPlayers(snapshot.totalPlayers());
    dailySentenceRepository.save(sentence);

    List<GameSession> sessions = gameSessionRepository.findAllBySentenceWithMember(sentence);
    Map<UUID, GameSession> sessionByPublicId =
        sessions.stream()
            .collect(Collectors.toMap(s -> s.getMember().getPublicId(), Function.identity()));

    List<GameSession> updatedSessions = new ArrayList<>();
    for (RankingSnapshot.MemberRank mr : snapshot.memberRanks()) {
      GameSession session = sessionByPublicId.get(mr.publicId());
      if (session != null) {
        session.recordFinalRank(mr.rank());
        updatedSessions.add(session);
      }
    }

    gameSessionRepository.saveAll(updatedSessions);
    log.info(
        "전날 랭킹 스냅샷 저장: date={}, updated={}, totalPlayers={}",
        yesterday,
        updatedSessions.size(),
        snapshot.totalPlayers());
  }

  private void notifyDiscord(MidnightJobReport report) {
    try {
      discordWebhookClient.send(NotificationLevel.INFO, buildEmbed(report));
    } catch (Exception e) {
      log.warn("Discord 웹훅 전송 중 예외 발생: {}", e.getMessage(), e);
    }
  }

  private DiscordEmbed buildEmbed(MidnightJobReport report) {
    String title;
    int color;
    if (!report.selectSucceeded()) {
      title = "자정 스케줄러 경고: 오늘 문장 선정 실패";
      color = COLOR_FAILURE;
    } else if (!report.expireSucceeded()
        || !report.snapshotSucceeded()
        || !report.keyExpireSucceeded()) {
      title = "자정 스케줄러 일부 실패";
      color = COLOR_PARTIAL;
    } else {
      title = "자정 스케줄러 실행 완료";
      color = COLOR_SUCCESS;
    }

    String description;
    if (report.todaySentence() != null) {
      String safeSentence = report.todaySentence().replace("|", "\\|");
      description = "오늘 문장: ||" + safeSentence + "||";
    } else {
      description = "오늘 문장이 선정되지 않았습니다.";
    }

    List<DiscordEmbed.Field> fields =
        List.of(
            new DiscordEmbed.Field("어제 세션 만료", statusLabel(report.expireSucceeded()), true),
            new DiscordEmbed.Field("랭킹 스냅샷", statusLabel(report.snapshotSucceeded()), true),
            new DiscordEmbed.Field("랭킹 키 만료", statusLabel(report.keyExpireSucceeded()), true),
            new DiscordEmbed.Field("오늘 문장 선정", statusLabel(report.selectSucceeded()), true),
            new DiscordEmbed.Field(
                "어제 참여자 수", String.valueOf(report.yesterdayTotalPlayers()), true),
            new DiscordEmbed.Field("미사용 문장 잔여", String.valueOf(report.unusedCount()), true));

    return new DiscordEmbed(title, description, color, fields);
  }

  private void selectTodaySentence(LocalDate today) {
    if (dailySentenceRepository.findByUsedAt(today).isPresent()) {
      log.info("오늘의 문장이 이미 선정됨: date={}", today);
      return;
    }

    DailySentence sentence =
        dailySentenceRepository
            .findByScheduledAt(today)
            .or(dailySentenceRepository::findRandomUnusedSentence)
            .orElseThrow(() -> new IllegalStateException("사용 가능한 문장이 없음 - 문장 추가 필요"));

    boolean wasScheduled = sentence.getScheduledAt() != null;
    sentence.setUsedAt(today);
    sentence.setStatus(DailySentenceStatus.USED);
    sentence.setScheduledAt(null);
    dailySentenceRepository.save(sentence);
    log.info(
        "오늘의 문장 선정: date={}, sentenceId={}, scheduled={}",
        today,
        sentence.getPublicId(),
        wasScheduled);
  }
}
