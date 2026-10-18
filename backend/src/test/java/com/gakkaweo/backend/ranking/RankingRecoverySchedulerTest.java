package com.gakkaweo.backend.ranking;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.ranking.service.RankingFinalizer;
import com.gakkaweo.backend.ranking.service.RankingRecoveryScheduler;
import com.gakkaweo.backend.ranking.service.RankingService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("랭킹 자동 복구 실행 설정 테스트")
class RankingRecoverySchedulerTest {

  private final RankingService rankingService = mock(RankingService.class);
  private final RankingFinalizer rankingFinalizer = mock(RankingFinalizer.class);
  private final DailySentenceRepository dailySentenceRepository =
      mock(DailySentenceRepository.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T00:00:00Z"), KST);
  private final RankingRecoveryScheduler rankingRecoveryScheduler =
      new RankingRecoveryScheduler(
          rankingService, rankingFinalizer, dailySentenceRepository, eventPublisher, clock);

  @Test
  @DisplayName("자동 복구를 꺼도 명시적으로 요청한 복구는 실행합니다")
  void 자동복구_중단과_명시적_복구() {
    ReflectionTestUtils.setField(rankingRecoveryScheduler, "recoveryEnabled", false);
    rankingRecoveryScheduler.onReady();
    rankingRecoveryScheduler.scheduledRecover();
    verifyNoInteractions(rankingService, rankingFinalizer, dailySentenceRepository, eventPublisher);

    rankingRecoveryScheduler.recover();

    verify(dailySentenceRepository).findDatesWithMissingFinalRank(LocalDate.now(clock));
    verify(dailySentenceRepository).findByUsedAt(LocalDate.now(clock));
  }

  @Test
  @DisplayName("자동 복구가 켜져 있으면 시작 시점과 예약 실행 모두 복구합니다")
  void 자동복구_실행() {
    ReflectionTestUtils.setField(rankingRecoveryScheduler, "recoveryEnabled", true);

    rankingRecoveryScheduler.onReady();
    rankingRecoveryScheduler.scheduledRecover();

    verify(dailySentenceRepository, times(2)).findDatesWithMissingFinalRank(LocalDate.now(clock));
    verify(dailySentenceRepository, times(2)).findByUsedAt(LocalDate.now(clock));
  }
}
