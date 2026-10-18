package com.gakkaweo.backend.ranking;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gakkaweo.backend.admin.dto.EmergencyReplaceRequest;
import com.gakkaweo.backend.admin.service.AdminAuditService;
import com.gakkaweo.backend.admin.service.AdminSentenceService;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.game.config.GameProperties;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.scheduler.DailySentenceScheduler;
import com.gakkaweo.backend.game.service.DailyGameService;
import com.gakkaweo.backend.game.util.HintMaskGenerator;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.ranking.service.RankingRecoveryScheduler;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.service.RankingWriteCoordinator;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("랭킹 best 기록과 자동 복구 정합성 통합 테스트")
class RankingRecoveryIntegrationTest extends IntegrationTestBase {
  @Autowired RankingService rankingService;
  @Autowired RankingRecoveryScheduler recovery;
  @Autowired RankingWriteCoordinator coordinator;
  @Autowired GameSessionRepository sessionRepository;
  @Autowired DailySentenceRepository sentenceRepository;
  @Autowired DailyGameService gameService;
  @Autowired StringRedisTemplate redis;

  @Autowired
  @Qualifier("transactionTemplate")
  TransactionTemplate transaction;

  @Autowired MeterRegistry registry;
  @Autowired AccountService accountService;
  @Autowired DailySentenceScheduler midnightScheduler;
  @Autowired GuessHistoryRepository historyRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired HintMaskGenerator hintMaskGenerator;
  @Autowired GameProperties gameProperties;
  @Autowired ApplicationEventPublisher publisher;
  @Autowired AdminAuditService auditService;

  @Test
  @DisplayName("best 이후 낮은 점수를 더 제출해도 재구축 전후 점수·시도 횟수는 동일")
  void 낮은_후속_추측_재구축_동일() {
    var sentence = testAuthHelper.createTodaySentence("복구 기준 문장");
    var member = testAuthHelper.createMember();
    GameSession session = seed(member, sentence, "90.0", 2);
    rankingService.updateRanking(session, member);
    String key = RedisKeyConstants.rankingKey(LocalDate.now(clock));
    String memberKey = RedisKeyConstants.memberKey(member.getPublicId());
    Double before = redis.opsForZSet().score(key, memberKey);
    transaction.executeWithoutResult(
        status -> {
          GameSession latest = sessionRepository.findById(session.getId()).orElseThrow();
          latest.incrementAttempt();
          latest.updateBestSimilarity(new BigDecimal("20.0"));
        });
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    assertThat(redis.opsForZSet().score(key, memberKey)).isEqualTo(before);
    assertThat(rankingService.getRankings().rankings().getFirst().attemptCount()).isEqualTo(2);
    assertThat(sessionRepository.findById(session.getId()).orElseThrow().getAttemptCount())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("Redis 데이터가 사라져도 현재 랭킹과 여러 날짜의 누락된 최종 순위를 DB에서 복구")
  void Redis_초기화_여러날_복구() {
    LocalDate today = LocalDate.now(clock);
    Member member = testAuthHelper.createMember();
    var todaySentence = testAuthHelper.createTodaySentence("현재 복구 문장");
    var oldSentence = testAuthHelper.createUsedSentence("이전 복구 문장", today.minusDays(3));
    var current = seed(member, todaySentence, "80.0", 1);
    var old = seed(member, oldSentence, "70.0", 2);
    rankingService.updateRanking(current, member);
    try (var connection = redis.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushAll();
    }
    recovery.recover();
    assertThat(rankingService.getRankings().rankings()).hasSize(1);
    assertThat(rankingService.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("80.0");
    assertThat(sessionRepository.findById(old.getId()).orElseThrow().getFinalRank()).isEqualTo(1);
    assertThat(sentenceRepository.findByUsedAt(today.minusDays(3)).orElseThrow().getTotalPlayers())
        .isEqualTo(1);
    // 과거 최종 순위는 캐시 재생성 없이 DB에서 확정합니다.
  }

  @Test
  @DisplayName("staging 단계 실패는 서비스 중인 이전 랭킹과 detail을 지우지 않는다")
  void 재구축_실패_기존값_보존() {
    var sentence = testAuthHelper.createTodaySentence("원자 복구 문장");
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90.0", 2);
    rankingService.updateRanking(session, member);
    String key = RedisKeyConstants.rankingKey(LocalDate.now(clock));
    String memberKey = RedisKeyConstants.memberKey(member.getPublicId());
    String detail = RedisKeyConstants.rankingDetailKey(LocalDate.now(clock), member.getPublicId());
    Double originalScore = redis.opsForZSet().score(key, memberKey);
    var originalDetail = redis.opsForHash().entries(detail);
    StringRedisTemplate failing = spy(redis);
    doThrow(new QueryTimeoutException("synthetic staging failure")).when(failing).opsForHash();
    var service =
        new RankingService(
            failing, sentenceRepository, sessionRepository, registry, clock, coordinator);
    assertThatThrownBy(() -> service.rebuildRankingCache(LocalDate.now(clock)))
        .isInstanceOf(QueryTimeoutException.class);
    assertThat(redis.opsForZSet().score(key, memberKey)).isEqualTo(originalScore);
    assertThat(redis.opsForHash().entries(detail)).isEqualTo(originalDetail);
  }

  @Test
  @DisplayName("오래된 best 캐시 쓰기는 나중에 달성한 더 높은 점수를 덮어쓰지 않는다")
  void 지연된_이전점수_무시() {
    var sentence = testAuthHelper.createTodaySentence("지연 복구 문장");
    var member = testAuthHelper.createMember();
    var older = seed(member, sentence, "70.0", 1);
    var newer =
        transaction.execute(
            status -> {
              var session = sessionRepository.findById(older.getId()).orElseThrow();
              session.incrementAttempt();
              session.updateBestSimilarity(new BigDecimal("90.0"));
              session.recordRankingImprovement(clock.instant());
              return session;
            });
    rankingService.updateRanking(newer, member);
    rankingService.updateRanking(older, member);
    assertThat(rankingService.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("90.0");
  }

  @Test
  @DisplayName("재구축과 실제 추측이 동시에 실행되어도 마지막 best를 유지한다")
  void 재구축_추측_경합() throws Exception {
    var sentence = testAuthHelper.createTodaySentence("동시 복구 문장");
    var member = testAuthHelper.createMember();
    seed(member, sentence, "90.0", 1);
    testSimilarityClient.setDefaultScore(new BigDecimal("93.0"));
    var barrier = new CyclicBarrier(2);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var rebuild =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return rankingService.rebuildRankingCache(LocalDate.now(clock));
              });
      var guess =
          executor.submit(
              () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return gameService.guessAuthenticated(
                    new GuessRequest(sentence.getPublicId(), "다음 추측"), member.getPublicId());
              });
      try {
        rebuild.get(15, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        assertThat(e.getCause())
            .isInstanceOf(com.gakkaweo.backend.common.exception.BusinessException.class);
      }
      assertThat(guess.get(15, TimeUnit.SECONDS).similarity()).isEqualByComparingTo("93.0");
    }
    recovery.recover();
    assertThat(rankingService.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("93.0");
    assertThat(rankingService.getRankings().rankings().getFirst().attemptCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("탈퇴 이후 지연된 이전 추측 callback이 회원을 랭킹에 복원하지 않는다")
  void 탈퇴_지연_갱신_차단() {
    var sentence = testAuthHelper.createTodaySentence("탈퇴 복구 문장");
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90.0", 1);
    rankingService.updateRanking(session, member);
    accountService.deleteAccount(member.getPublicId());
    rankingService.cleanupMemberRanking(member.getPublicId());
    assertThat(rankingService.updateRanking(session, member)).isNull();
    recovery.recover();
    assertThat(rankingService.getRankings().rankings()).isEmpty();
  }

  @Test
  @DisplayName("재구축 준비 중 낮거나 같은 점수의 추측은 저장하면서 캐시 전환을 취소하지 않습니다")
  void 재구축_중_낮은_동점_추측_저장() throws Exception {
    var sentence = testAuthHelper.createTodaySentence("최고 기록 유지 복구 문장");
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90.0", 2);
    var entered = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    StringRedisTemplate delayed = spy(redis);
    doAnswer(
            invocation -> {
              entered.countDown();
              assertThat(proceed.await(10, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when(delayed)
        .opsForHash();
    var service =
        new RankingService(
            delayed, sentenceRepository, sessionRepository, registry, clock, coordinator);
    long revision = coordinator.revision();

    try (var executor = Executors.newSingleThreadExecutor()) {
      var rebuild = executor.submit(() -> service.rebuildRankingCache(LocalDate.now(clock)));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      testSimilarityClient.setDefaultScore(new BigDecimal("50.0"));
      gameService.guessAuthenticated(
          new GuessRequest(sentence.getPublicId(), "낮은 점수 추측"), member.getPublicId());
      testSimilarityClient.setDefaultScore(new BigDecimal("90.0"));
      gameService.guessAuthenticated(
          new GuessRequest(sentence.getPublicId(), "동점 추측"), member.getPublicId());

      assertThat(coordinator.revision()).isEqualTo(revision);
      proceed.countDown();
      assertThat(rebuild.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    } finally {
      proceed.countDown();
    }

    assertThat(sessionRepository.findById(session.getId()).orElseThrow().getAttemptCount())
        .isEqualTo(4);
    assertThat(historyRepository.count()).isEqualTo(2);
    assertThat(service.getRankings().rankings().getFirst().attemptCount()).isEqualTo(2);
    assertThat(service.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("90.0");
  }

  @Test
  @DisplayName("게임 변경이 발생하면 staging revision 충돌을 감지해 이전 snapshot을 교체하지 않는다")
  void staging_중_회원_탈퇴_경합() throws Exception {
    var sentence = testAuthHelper.createTodaySentence("탈퇴 경쟁 문장");
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90.0", 1);
    rankingService.updateRanking(session, member);
    var entered = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    StringRedisTemplate delayed = spy(redis);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              entered.countDown();
              assertThat(proceed.await(10, TimeUnit.SECONDS)).isTrue();
              return redis.opsForHash();
            })
        .when(delayed)
        .opsForHash();
    var service =
        new RankingService(
            delayed, sentenceRepository, sessionRepository, registry, clock, coordinator);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var rebuild = executor.submit(() -> service.rebuildRankingCache(LocalDate.now(clock)));
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      accountService.deleteAccount(member.getPublicId());
      rankingService.cleanupMemberRanking(member.getPublicId());
      proceed.countDown();
      assertThatThrownBy(() -> rebuild.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(com.gakkaweo.backend.common.exception.BusinessException.class);
    } finally {
      proceed.countDown();
    }
    assertThat(rankingService.getRankings().rankings()).isEmpty();
  }

  @Test
  @DisplayName("Redis에 일부 참가자만 남아 있어도 자정 최종 순위는 전체 DB 기록으로 확정한다")
  void 자정_부분_Redis_스냅샷_방지() {
    LocalDate date = LocalDate.now(clock);
    var sentence = testAuthHelper.createTodaySentence("부분 자정 문장");
    var lowMember = testAuthHelper.createMember();
    var highMember = testAuthHelper.createMember();
    var low = seed(lowMember, sentence, "50.0", 1);
    var high = seed(highMember, sentence, "90.0", 1);
    rankingService.updateRanking(low, lowMember);
    testAuthHelper.createActiveSentence("다음 날짜 후보");
    ((TestClock) clock).advanceBy(java.time.Duration.ofDays(1));
    midnightScheduler.executeMidnightJob();
    assertThat(sessionRepository.findById(low.getId()).orElseThrow().getFinalRank()).isEqualTo(2);
    assertThat(sessionRepository.findById(high.getId()).orElseThrow().getFinalRank()).isEqualTo(1);
    assertThat(sentenceRepository.findByUsedAt(date).orElseThrow().getTotalPlayers()).isEqualTo(2);
  }

  @Test
  @DisplayName("과거 부분 스냅샷은 기존 순위도 함께 재확정해 중복 1위와 참가자 수를 복구한다")
  void 과거_부분_스냅샷_전체_복구() {
    LocalDate date = LocalDate.now(clock).minusDays(1);
    var sentence = testAuthHelper.createUsedSentence("부분 과거 문장", date);
    var low = seed(testAuthHelper.createMember(), sentence, "50.0", 1);
    var high = seed(testAuthHelper.createMember(), sentence, "90.0", 1);
    transaction.executeWithoutResult(
        status -> {
          sessionRepository.findById(low.getId()).orElseThrow().recordFinalRank(1);
          sentenceRepository.findByUsedAt(date).orElseThrow().recordTotalPlayers(1);
        });
    recovery.recover();
    assertThat(sessionRepository.findById(low.getId()).orElseThrow().getFinalRank()).isEqualTo(2);
    assertThat(sessionRepository.findById(high.getId()).orElseThrow().getFinalRank()).isEqualTo(1);
    assertThat(sentenceRepository.findByUsedAt(date).orElseThrow().getTotalPlayers()).isEqualTo(2);
  }

  @Test
  @DisplayName("완전히 확정된 과거 순위와 참가자 수는 회원 탈퇴 후에도 재계산하지 않는다")
  void 완결_과거_탈퇴_통계_보존() {
    LocalDate date = LocalDate.now(clock).minusDays(1);
    var sentence = testAuthHelper.createUsedSentence("완결 과거 문장", date);
    var low = seed(testAuthHelper.createMember(), sentence, "50.0", 1);
    var highMember = testAuthHelper.createMember();
    var high = seed(highMember, sentence, "90.0", 1);
    recovery.recover();
    accountService.deleteAccount(highMember.getPublicId());
    recovery.recover();
    assertThat(sessionRepository.findById(high.getId()).orElseThrow().getMember()).isNull();
    assertThat(sessionRepository.findById(high.getId()).orElseThrow().getFinalRank()).isEqualTo(1);
    assertThat(sessionRepository.findById(low.getId()).orElseThrow().getFinalRank()).isEqualTo(2);
    assertThat(sentenceRepository.findByUsedAt(date).orElseThrow().getTotalPlayers()).isEqualTo(2);
  }

  @Test
  @DisplayName("Redis 첫 실패 뒤 다음 추측은 캐시 명령 없이 DB에 저장하고 복구 뒤 쓰기를 재개한다")
  @SuppressWarnings("unchecked")
  void Redis_장애_circuit_후_복구() {
    var sentence = testAuthHelper.createTodaySentence("circuit 문장");
    var member = testAuthHelper.createMember();
    StringRedisTemplate failing = spy(redis);
    var calls = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (calls.incrementAndGet() == 1) {
                throw new QueryTimeoutException("synthetic live failure");
              }
              return invocation.callRealMethod();
            })
        .when(failing)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));
    var localRanking =
        new RankingService(
            failing, sentenceRepository, sessionRepository, registry, clock, coordinator);
    ReflectionTestUtils.invokeMethod(localRanking, "initCounters");
    var game =
        new DailyGameService(
            sentenceRepository,
            sessionRepository,
            historyRepository,
            coordinator,
            memberRepository,
            testSimilarityClient,
            localRanking,
            hintMaskGenerator,
            gameProperties,
            publisher,
            clock,
            registry,
            transaction);
    ReflectionTestUtils.invokeMethod(game, "initCounters");
    testSimilarityClient.setDefaultScore(new BigDecimal("50.0"));
    game.guessAuthenticated(new GuessRequest(sentence.getPublicId(), "첫 추측"), member.getPublicId());
    assertThat(localRanking.isCacheCircuitOpen()).isTrue();
    testSimilarityClient.setDefaultScore(new BigDecimal("90.0"));
    game.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "두 번째 추측"), member.getPublicId());
    verify(failing, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    assertThat(
            sessionRepository
                .findByMemberAndSentence(member, sentence)
                .orElseThrow()
                .getBestSimilarity())
        .isEqualByComparingTo("90.0");
    localRanking.rebuildRankingCache(LocalDate.now(clock));
    assertThat(localRanking.isCacheCircuitOpen()).isFalse();
    testSimilarityClient.setDefaultScore(new BigDecimal("95.0"));
    game.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "세 번째 추측"), member.getPublicId());
    assertThat(localRanking.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("95.0");
    assertThat(calls.get())
        .isEqualTo(3); // failed live command + recovery swap + resumed live command
  }

  @Test
  @DisplayName("긴급 문장 교체의 Redis 정리가 실패해도 DB 교체·알림은 성공하며 오래된 랭킹을 복구한다")
  void 긴급_교체_캐시_실패_복구() {
    LocalDate today = LocalDate.now(clock);
    var oldSentence = testAuthHelper.createTodaySentence("교체 이전 문장");
    var member = testAuthHelper.createMember();
    var old = seed(member, oldSentence, "100.0", 1);
    rankingService.updateRanking(old, member);
    rankingService.rebuildRankingCache(today);
    var newSentence = testAuthHelper.createActiveSentence("교체 새 문장");
    var admin = testAuthHelper.createAdmin();
    StringRedisTemplate failing = spy(redis);
    doThrow(new QueryTimeoutException("synthetic swap failure"))
        .when(failing)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));
    var failedRanking =
        new RankingService(
            failing, sentenceRepository, sessionRepository, registry, clock, coordinator);
    var service =
        new AdminSentenceService(
            sentenceRepository,
            sessionRepository,
            historyRepository,
            testSimilarityClient,
            publisher,
            transaction,
            auditService,
            clock,
            coordinator,
            failedRanking);
    var response =
        service.emergencyReplace(
            new EmergencyReplaceRequest(newSentence.getPublicId(), true),
            admin.getPublicId(),
            "127.0.0.1");
    assertThat(response.publicId()).isEqualTo(newSentence.getPublicId());
    assertThat(testEventCollector.getDayChangeEvents())
        .extracting(DayChangeEvent::newSentenceId)
        .contains(newSentence.getPublicId());
    assertThat(failedRanking.needsRecovery(today)).isTrue();
    var latest = seed(member, sentenceRepository.findByUsedAt(today).orElseThrow(), "10.0", 1);
    assertThat(failedRanking.updateRanking(latest, member)).isNull();
    recovery.recover();
    assertThat(rankingService.isCacheCircuitOpen()).isFalse();
    assertThat(rankingService.getRankings().rankings().getFirst().similarity())
        .isEqualByComparingTo("10.0");
  }

  private GameSession seed(Member member, DailySentence sentence, String best, int attempts) {
    return transaction.execute(
        status -> {
          GameSession session = new GameSession(member, sentence);
          for (int i = 0; i < attempts; i++) {
            session.incrementAttempt();
          }
          session.updateBestSimilarity(new BigDecimal(best));
          session.recordRankingImprovement(
              sentence.getUsedAt().atStartOfDay(KST).plusHours(12).toInstant());
          return sessionRepository.save(session);
        });
  }
}
