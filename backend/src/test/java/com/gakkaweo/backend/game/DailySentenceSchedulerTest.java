package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GameSessionStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.scheduler.DailySentenceScheduler;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DailySentenceScheduler 통합 테스트")
class DailySentenceSchedulerTest extends IntegrationTestBase {

  @Autowired DailySentenceScheduler scheduler;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired Clock schedulerClock;
  @Autowired RankingService rankingService;
  @Autowired StringRedisTemplate redisTemplate;

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

  @Test
  @DisplayName("executeMidnightJob - 랭킹 데이터 있으면 recordFinalRank + totalPlayers 기록")
  void 스냅샷_저장_경로() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate yesterday = LocalDate.now(testClock);

    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("어제 정답 문장");
              s.setUsedAt(yesterday);
              s.setStatus(DailySentenceStatus.USED);
              dailySentenceRepository.save(s);
              GameSession session = new GameSession(member, s);
              session.updateBestSimilarity(new BigDecimal("100.0"));
              session.markCleared(testClock.instant());
              gameSessionRepository.save(session);
              return s;
            });
    GameSession session =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    rankingService.updateRanking(session, member);

    testAuthHelper.createActiveSentence("오늘 후보");
    testClock.advanceBy(Duration.ofDays(1));

    scheduler.executeMidnightJob();

    DailySentence reloadedYesterday =
        dailySentenceRepository.findById(yesterdaySentence.getId()).orElseThrow();
    assertThat(reloadedYesterday.getTotalPlayers()).isGreaterThanOrEqualTo(1);

    GameSession finalSession =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    assertThat(finalSession.getFinalRank()).isEqualTo(1);
  }

  @Test
  @DisplayName("executeMidnightJob - 두 번 호출해도 finalRank/totalPlayers 동일 (멱등)")
  void 스냅샷_저장_멱등성() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate yesterday = LocalDate.now(testClock);

    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("어제 정답 문장 멱등");
              s.setUsedAt(yesterday);
              s.setStatus(DailySentenceStatus.USED);
              dailySentenceRepository.save(s);
              GameSession session = new GameSession(member, s);
              session.updateBestSimilarity(new BigDecimal("100.0"));
              session.markCleared(testClock.instant());
              gameSessionRepository.save(session);
              return s;
            });
    GameSession session =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    rankingService.updateRanking(session, member);

    testAuthHelper.createActiveSentence("오늘 후보 멱등");
    testClock.advanceBy(Duration.ofDays(1));

    scheduler.executeMidnightJob();

    DailySentence afterFirst =
        dailySentenceRepository.findById(yesterdaySentence.getId()).orElseThrow();
    Integer totalAfterFirst = afterFirst.getTotalPlayers();
    GameSession sessionAfterFirst =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    Integer rankAfterFirst = sessionAfterFirst.getFinalRank();
    assertThat(totalAfterFirst).isNotNull().isGreaterThanOrEqualTo(1);
    assertThat(rankAfterFirst).isNotNull().isGreaterThanOrEqualTo(1);

    scheduler.executeMidnightJob();

    DailySentence afterSecond =
        dailySentenceRepository.findById(yesterdaySentence.getId()).orElseThrow();
    GameSession sessionAfterSecond =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();

    assertThat(afterSecond.getTotalPlayers()).isEqualTo(totalAfterFirst);
    assertThat(sessionAfterSecond.getFinalRank()).isEqualTo(rankAfterFirst);
  }

  @Test
  @DisplayName("executeMidnightJob - Redis 키 만료 후 두 번째 호출(empty snapshot)도 첫 결과 유지 (멱등)")
  void 스냅샷_저장_멱등성_empty_snapshot() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate yesterday = LocalDate.now(testClock);

    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("어제 정답 문장 멱등 empty");
              s.setUsedAt(yesterday);
              s.setStatus(DailySentenceStatus.USED);
              dailySentenceRepository.save(s);
              GameSession gs = new GameSession(member, s);
              gs.updateBestSimilarity(new BigDecimal("100.0"));
              gs.markCleared(testClock.instant());
              gameSessionRepository.save(gs);
              return s;
            });
    GameSession session =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();
    rankingService.updateRanking(session, member);

    testAuthHelper.createActiveSentence("오늘 후보 멱등 empty");
    testClock.advanceBy(Duration.ofDays(1));

    scheduler.executeMidnightJob();

    Integer totalAfterFirst =
        dailySentenceRepository.findById(yesterdaySentence.getId()).orElseThrow().getTotalPlayers();
    Integer rankAfterFirst =
        gameSessionRepository
            .findByMemberAndSentence(member, yesterdaySentence)
            .orElseThrow()
            .getFinalRank();
    assertThat(totalAfterFirst).isNotNull().isGreaterThanOrEqualTo(1);
    assertThat(rankAfterFirst).isNotNull().isGreaterThanOrEqualTo(1);

    redisTemplate.delete(RedisKeyConstants.rankingKey(yesterday));
    redisTemplate.delete(RedisKeyConstants.rankingDetailKey(yesterday, member.getPublicId()));

    scheduler.executeMidnightJob();

    DailySentence afterSecond =
        dailySentenceRepository.findById(yesterdaySentence.getId()).orElseThrow();
    GameSession sessionAfterSecond =
        gameSessionRepository.findByMemberAndSentence(member, yesterdaySentence).orElseThrow();

    assertThat(afterSecond.getTotalPlayers()).isEqualTo(totalAfterFirst);
    assertThat(sessionAfterSecond.getFinalRank()).isEqualTo(rankAfterFirst);
  }

  @Test
  @DisplayName("executeMidnightJob - 예약 문장(scheduledAt=today)이 우선 선정")
  void 예약문장_우선선정() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate today = LocalDate.now(testClock);

    DailySentence scheduled =
        transactionTemplate.execute(
            status -> {
              DailySentence s = new DailySentence("예약된 문장");
              s.setScheduledAt(today);
              return dailySentenceRepository.save(s);
            });
    testAuthHelper.createActiveSentence("미예약 후보");

    scheduler.executeMidnightJob();

    DailySentence picked = dailySentenceRepository.findByUsedAt(today).orElseThrow();
    assertThat(picked.getId()).isEqualTo(scheduled.getId());
    assertThat(picked.getScheduledAt()).isNull();
    assertThat(picked.getStatus()).isEqualTo(DailySentenceStatus.USED);
  }

  @Test
  @DisplayName("executeMidnightJob - 이미 오늘 문장 존재 시 재선정 안 함")
  void 오늘문장_이미존재() {
    TestClock testClock = (TestClock) schedulerClock;
    LocalDate today = LocalDate.now(testClock);

    DailySentence existing = testAuthHelper.createTodaySentence("이미 선정된 문장");
    testAuthHelper.createActiveSentence("추가 후보");

    scheduler.executeMidnightJob();

    DailySentence picked = dailySentenceRepository.findByUsedAt(today).orElseThrow();
    assertThat(picked.getId()).isEqualTo(existing.getId());
  }

  @Test
  @DisplayName("executeMidnightJob - 사용 가능한 문장이 없어도 예외 로깅 후 흐름 유지")
  void 후보문장_없음() {
    scheduler.executeMidnightJob();

    LocalDate today = LocalDate.now(schedulerClock);
    assertThat(dailySentenceRepository.findByUsedAt(today)).isEmpty();
  }

  @Test
  @DisplayName("onApplicationReady - 오늘 문장 이미 있으면 즉시 선정 건너뜀")
  void 앱시작_이미선정됨() {
    DailySentence existing = testAuthHelper.createTodaySentence("이미 선정");

    scheduler.onApplicationReady();

    LocalDate today = LocalDate.now(schedulerClock);
    DailySentence picked = dailySentenceRepository.findByUsedAt(today).orElseThrow();
    assertThat(picked.getId()).isEqualTo(existing.getId());
  }
}
