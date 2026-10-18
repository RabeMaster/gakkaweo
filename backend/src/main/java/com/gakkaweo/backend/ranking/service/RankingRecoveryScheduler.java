package com.gakkaweo.backend.ranking.service;

import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingRecoveryScheduler {

  private final RankingService rankingService;
  private final RankingFinalizer rankingFinalizer;
  private final DailySentenceRepository dailySentenceRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Value("${app.ranking.recovery-enabled:true}")
  private boolean recoveryEnabled;

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    if (!recoveryEnabled) {
      return;
    }
    recover();
  }

  @Scheduled(
      fixedDelayString = "${app.ranking.recovery-delay:60000}",
      initialDelayString = "${app.ranking.recovery-initial-delay:60000}")
  public void scheduledRecover() {
    if (!recoveryEnabled) {
      return;
    }
    recover();
  }

  public void recover() {
    LocalDate today = LocalDate.now(clock);
    try {
      for (LocalDate date :
          dailySentenceRepository.findDatesWithMissingFinalRank(today).stream().limit(7).toList()) {
        try {
          // 과거 최종 순위의 DB 커밋은 Redis 복구 여부와 무관합니다.
          rankingFinalizer.finalizeForDate(date);
        } catch (RuntimeException e) {
          log.warn("과거 순위 확정을 다음 주기에 재시도합니다: date={}", date, e);
        }
      }
      if (dailySentenceRepository.findByUsedAt(today).isPresent()
          && rankingService.needsRecovery(today)) {
        rankingService.rebuildRankingCache(today);
        eventPublisher.publishEvent(new RankingUpdateEvent());
      }
    } catch (RuntimeException e) {
      log.warn("랭킹 복구를 다음 주기에 재시도합니다", e);
    }
  }
}
