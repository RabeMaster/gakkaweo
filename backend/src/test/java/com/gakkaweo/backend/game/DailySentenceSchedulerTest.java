package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GameSessionStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.scheduler.DailySentenceScheduler;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DailySentenceScheduler 통합 테스트")
class DailySentenceSchedulerTest extends IntegrationTestBase {

  @Autowired DailySentenceScheduler scheduler;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired Clock schedulerClock;

  @Test
  @DisplayName("executeMidnightJob - 전날 세션 EXPIRED + 오늘 문장 USED + DayChangeEvent 발행")
  void 자정_스케줄러_실행() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate yesterday = LocalDate.now(testClock);

    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("어제 문장");
              s.setUsedAt(yesterday);
              s.setStatus(DailySentenceStatus.USED);
              dailySentenceRepository.save(s);
              gameSessionRepository.save(new GameSession(member, s));
              return s;
            });

    testAuthHelper.createActiveSentence("미사용 후보");

    testClock.advanceBy(Duration.ofDays(1));

    scheduler.executeMidnightJob();

    LocalDate today = LocalDate.now(testClock);

    GameSession reloaded =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(GameSessionStatus.EXPIRED);

    DailySentence todayPicked = dailySentenceRepository.findByUsedAt(today).orElseThrow();
    assertThat(todayPicked.getStatus()).isEqualTo(DailySentenceStatus.USED);

    assertThat(testEventCollector.getDayChangeEvents()).isNotEmpty();
  }

  @Test
  @DisplayName("onApplicationReady - 오늘 문장 없으면 즉시 선정")
  void 앱시작_즉시선정() {
    testAuthHelper.createActiveSentence("즉시 후보");

    scheduler.onApplicationReady();

    LocalDate today = LocalDate.now(schedulerClock);
    assertThat(dailySentenceRepository.findByUsedAt(today)).isPresent();
  }
}
