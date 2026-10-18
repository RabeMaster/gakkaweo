package com.gakkaweo.backend.ranking;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.member.service.MemberRedisSyncer;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingFinalizer;
import com.gakkaweo.backend.ranking.service.RankingRecoveryScheduler;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.service.RankingWriteCoordinator;
import com.gakkaweo.backend.support.IntegrationTestBase;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("랭킹 장애 계약·정본 복구 통합 테스트")
class RankingContractTest extends IntegrationTestBase {

  @Autowired RankingService rankingService;
  @Autowired RankingFinalizer rankingFinalizer;
  @Autowired RankingWriteCoordinator rankingWriteCoordinator;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @MockitoSpyBean GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired StringRedisTemplate redisTemplate;
  @Autowired MeterRegistry meterRegistry;
  @Autowired MemberRedisSyncer memberRedisSyncer;
  @Autowired MemberRepository memberRepository;
  @Autowired RankingRecoveryScheduler rankingRecoveryScheduler;

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("복구 중 차단된 조회는 성공한 복구 뒤 추가 복구 표시를 남기지 않습니다")
  void 복구_중_조회_추가복구_방지() {
    var sentence = testAuthHelper.createTodaySentence("복구 중 조회 문장");
    seed(testAuthHelper.createMember(), sentence, "90", 2, clock.instant());
    LocalDate today = LocalDate.now(clock);
    var redis = spy(redisTemplate);
    var service =
        new RankingService(
            redis,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);
    service.recordCacheFailure(today);
    doAnswer(
            invocation -> {
              for (int i = 0; i < 3; i++) {
                assertThatThrownBy(service::getRankings)
                    .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RANKING_UNAVAILABLE));
              }
              return invocation.callRealMethod();
            })
        .when(redis)
        .opsForHash();

    service.rebuildRankingCache(today);

    assertThat(service.isCacheCircuitOpen()).isFalse();
    assertThat(service.needsRecovery(today)).isFalse();
    assertThat(service.getRankings().rankings()).hasSize(1);
    verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("복구 중 실제로 추가된 복구 표시는 성공한 전환 뒤에도 보존합니다")
  void 복구_중_새로운_복구표시_보존() {
    var sentence = testAuthHelper.createTodaySentence("새 복구 표시 문장");
    seed(testAuthHelper.createMember(), sentence, "90", 2, clock.instant());
    LocalDate today = LocalDate.now(clock);
    var redis = spy(redisTemplate);
    var service =
        new RankingService(
            redis,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);
    service.recordCacheFailure(today);
    doAnswer(
            invocation -> {
              service.markRecoveryNeeded(today);
              return invocation.callRealMethod();
            })
        .when(redis)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));

    service.rebuildRankingCache(today);

    assertThat(service.isCacheCircuitOpen()).isFalse();
    assertThat(service.needsRecovery(today)).isTrue();
  }

  @Test
  @DisplayName("실제 Redis 조회 실패는 회로를 열고 복구 대상으로 기록합니다")
  void 실제_조회_실패_복구표시() {
    var sentence = testAuthHelper.createTodaySentence("조회 실패 문장");
    seed(testAuthHelper.createMember(), sentence, "90", 2, clock.instant());
    LocalDate today = LocalDate.now(clock);
    rankingService.rebuildRankingCache(today);
    var redis = spy(redisTemplate);
    doThrow(new QueryTimeoutException("synthetic read failure"))
        .when(redis)
        .executePipelined(any(RedisCallback.class));
    var service =
        new RankingService(
            redis,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);

    assertThatThrownBy(service::getRankings)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RANKING_UNAVAILABLE));

    assertThat(service.isCacheCircuitOpen()).isTrue();
    assertThat(service.needsRecovery(today)).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("정상 랭킹 조회는 DB 전체 대조 한 번과 Redis 배치 조회로 동일한 순위를 반환합니다")
  void 정상_조회_단일_대조와_배치() {
    var sentence = testAuthHelper.createTodaySentence("배치 조회 문장");
    for (int i = 0; i < 12; i++) {
      seed(testAuthHelper.createMember(), sentence, "90.0", i + 1, clock.instant());
    }
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    var expected = rankingService.getRankings();
    clearInvocations(gameSessionRepository);
    var redis = spy(redisTemplate);
    var service =
        new RankingService(
            redis,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);

    var actual = service.getRankings();

    assertThat(actual).isEqualTo(expected);
    assertThat(actual.rankings()).hasSize(10);
    assertThat(actual.totalPlayers()).isEqualTo(12);
    verify(gameSessionRepository, times(1)).findAllBySentenceWithMember(any());
    verify(redis, times(2)).executePipelined(any(RedisCallback.class));
    verify(redis, never()).opsForHash();
  }

  @Test
  @DisplayName("배치 대조는 상위 목록 밖의 점수와 상세 손상도 복구합니다")
  void 상위_목록_밖_배치_손상_복구() {
    var sentence = testAuthHelper.createTodaySentence("전체 참가자 배치 문장");
    for (int i = 0; i < 10; i++) {
      seed(testAuthHelper.createMember(), sentence, "90.0", 1, clock.instant());
    }
    var outsider = testAuthHelper.createMember();
    seed(outsider, sentence, "80.0", 2, clock.instant());
    LocalDate today = LocalDate.now(clock);
    rankingService.rebuildRankingCache(today);
    var expected = rankingService.getFullRankingsForDate(today);

    redisTemplate
        .opsForZSet()
        .add(
            RedisKeyConstants.rankingKey(today),
            RedisKeyConstants.memberKey(outsider.getPublicId()),
            1);

    assertThat(rankingService.needsRecovery(today)).isTrue();
    rankingService.getRankings();
    assertThat(rankingService.getFullRankingsForDate(today)).isEqualTo(expected);

    redisTemplate
        .opsForHash()
        .put(RedisKeyConstants.rankingDetailKey(today, outsider.getPublicId()), "nickname", "손상");

    assertThat(rankingService.needsRecovery(today)).isTrue();
    rankingService.getRankings();
    assertThat(rankingService.getFullRankingsForDate(today)).isEqualTo(expected);
  }

  @Test
  @DisplayName("정상 0명은 200, 신뢰할 수 없는 랭킹은 503/RANKING_UNAVAILABLE")
  void 정상_빈목록과_장애_구분() {
    testAuthHelper.createTodaySentence("빈 문장");
    assertThat(
            restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    rankingService.recordCacheFailure(LocalDate.now(clock));
    try {
      var failed =
          restTemplate.getForEntity(
              url("/ranking/today"), com.gakkaweo.backend.common.exception.ErrorBody.class);
      assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(failed.getBody().code()).isEqualTo("RANKING_UNAVAILABLE");
    } finally {
      rankingService.rebuildRankingCache(LocalDate.now(clock));
    }
  }

  @Test
  @DisplayName("다른 문제 문맥으로 요청한 랭킹은 목록 대신 503")
  void 이전_문제_랭킹_차단() {
    testAuthHelper.createTodaySentence("현재 문장");
    LocalDate today = LocalDate.now(clock);
    rankingService.rebuildRankingCache(today);

    assertThat(
            restTemplate
                .getForEntity(url("/ranking/today?sentenceId=" + UUID.randomUUID()), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    assertThat(rankingService.needsRecovery(today)).isFalse();
  }

  @Test
  @DisplayName("marker가 있어도 부분 유실·내용 손상·잘못된 타입을 복구")
  void 부분_손상_복구() {
    var sentence = testAuthHelper.createTodaySentence("손상 문장");
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90", 2, clock.instant());
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    var expected = rankingService.getRankings();
    String detail = RedisKeyConstants.rankingDetailKey(LocalDate.now(clock), member.getPublicId());
    redisTemplate.opsForHash().put(detail, "similarity", "1");
    assertThat(rankingService.needsRecovery(LocalDate.now(clock))).isTrue();
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    assertThat(rankingService.getRankings()).isEqualTo(expected);
    redisTemplate.delete(detail);
    redisTemplate.opsForValue().set(detail, "wrong type");
    assertThat(rankingService.needsRecovery(LocalDate.now(clock))).isTrue();
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    assertThat(rankingService.getRankings()).isEqualTo(expected);
    redisTemplate.delete(RedisKeyConstants.rankingKey(LocalDate.now(clock)));
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    assertThat(rankingService.getRankings()).isEqualTo(expected);
    assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getBestSimilarity())
        .isEqualByComparingTo("90");
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("staging 사전 타입 검사 실패는 기존 캐시를 변경하지 않습니다")
  void staging_검증_실패_원본_보존() {
    var sentence = testAuthHelper.createTodaySentence("staging 문장");
    var member = testAuthHelper.createMember();
    seed(member, sentence, "90", 2, clock.instant());
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    var expected = rankingService.getRankings();
    StringRedisTemplate corrupt = spy(redisTemplate);
    doAnswer(
            invocation -> {
              var keys = invocation.<List<String>>getArgument(1);
              redisTemplate.delete(keys.get(4));
              redisTemplate.opsForValue().set(keys.get(4), "wrong staging type");
              return invocation.callRealMethod();
            })
        .when(corrupt)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));
    var local =
        new RankingService(
            corrupt,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);

    assertThatThrownBy(() -> local.rebuildRankingCache(LocalDate.now(clock)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(rankingService.getRankings()).isEqualTo(expected);
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("staging 상세 범위 오류도 정상 캐시를 변경하지 않습니다")
  void staging_값_검증_보존() {
    var sentence = testAuthHelper.createTodaySentence("staging 값 문장");
    var member = testAuthHelper.createMember();
    seed(member, sentence, "90", 2, clock.instant());
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    var expected = rankingService.getRankings();
    StringRedisTemplate corrupt = spy(redisTemplate);
    doAnswer(
            invocation -> {
              var keys = invocation.<List<String>>getArgument(1);
              redisTemplate.opsForHash().put(keys.get(4), "similarity", "999");
              return invocation.callRealMethod();
            })
        .when(corrupt)
        .execute(any(RedisScript.class), anyList(), any(Object[].class));
    var local =
        new RankingService(
            corrupt,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            rankingWriteCoordinator);

    assertThatThrownBy(() -> local.rebuildRankingCache(LocalDate.now(clock)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(rankingService.getRankings()).isEqualTo(expected);
  }

  @Test
  @DisplayName("날짜 밖 대체 시각은 유사도와 시도 횟수 우선순위를 침범하지 않습니다")
  void 대체_시각_성분_제한() {
    var sentence = testAuthHelper.createTodaySentence("대체 시각 문장");
    Instant start = LocalDate.now(clock).atStartOfDay(KST).toInstant();
    var fewer = testAuthHelper.createMember();
    var more = testAuthHelper.createMember();
    var lower = testAuthHelper.createMember();
    seed(fewer, sentence, "90", 1, start.plusSeconds(86400L * 50));
    seed(more, sentence, "90", 2, start.plusSeconds(1));
    seed(lower, sentence, "89.9", 1, start.plusSeconds(1));

    rankingService.rebuildRankingCache(LocalDate.now(clock));

    assertThat(rankingService.getRankings().rankings())
        .extracting(RankingResponse.RankingEntry::publicId)
        .containsExactly(fewer.getPublicId(), more.getPublicId(), lower.getPublicId());
    assertThat(rankingService.getCanonicalSnapshotForDate(LocalDate.now(clock)).memberRanks())
        .extracting(com.gakkaweo.backend.ranking.dto.RankingSnapshot.MemberRank::publicId)
        .containsExactly(fewer.getPublicId(), more.getPublicId(), lower.getPublicId());
  }

  @Test
  @DisplayName("재시작 후 복구와 최종 순위는 달성 시각·999 상한·UUID 동점 순서가 동일")
  void 동점_복구와_확정_동일() {
    var sentence = testAuthHelper.createTodaySentence("동점 문장");
    Instant time = LocalDate.now(clock).atStartOfDay(KST).plusHours(1).toInstant();
    var a = testAuthHelper.createMember();
    var b = testAuthHelper.createMember();
    var c = testAuthHelper.createMember();
    var d = testAuthHelper.createMember();
    seed(a, sentence, "90", 1000, time);
    seed(b, sentence, "90", 1200, time);
    seed(c, sentence, "100", 5, time.plusMillis(10));
    seed(d, sentence, "100", 10, time);
    rankingService.rebuildRankingCache(LocalDate.now(clock));
    var before = rankingService.getRankings();
    assertThat(before.rankings().get(0).publicId()).isEqualTo(d.getPublicId());
    assertThat(before.rankings().get(1).publicId()).isEqualTo(c.getPublicId());
    var ids =
        java.util.stream.Stream.of(a.getPublicId(), b.getPublicId())
            .sorted(java.util.Comparator.comparing(UUID::toString).reversed())
            .toList();
    assertThat(before.rankings().subList(2, 4))
        .extracting(RankingResponse.RankingEntry::publicId)
        .containsExactlyElementsOf(ids);
    assertThat(before.rankings())
        .extracting(RankingResponse.RankingEntry::attemptCount)
        .contains(1000, 1200);
    var restarted =
        new RankingService(
            redisTemplate,
            dailySentenceRepository,
            gameSessionRepository,
            meterRegistry,
            clock,
            new RankingWriteCoordinator());
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(restarted, "initCounters");
    restarted.rebuildRankingCache(LocalDate.now(clock));
    assertThat(restarted.getRankings()).isEqualTo(before);
    rankingService.recordCacheFailure(LocalDate.now(clock));
    try {
      var snapshot = rankingFinalizer.finalizeForDate(LocalDate.now(clock));
      assertThat(snapshot.memberRanks())
          .extracting(com.gakkaweo.backend.ranking.dto.RankingSnapshot.MemberRank::publicId)
          .containsExactlyElementsOf(
              before.rankings().stream().map(RankingResponse.RankingEntry::publicId).toList());
    } finally {
      rankingService.rebuildRankingCache(LocalDate.now(clock));
    }
  }

  @Test
  @DisplayName("0명과 전체 순위 누락 날짜도 확정하며 저장된 참여자 수는 줄이지 않습니다")
  void 복구_대상과_참여자_보존() {
    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    var empty = testAuthHelper.createUsedSentence("빈 과거", yesterday);
    rankingFinalizer.finalizeForDate(yesterday);
    assertThat(dailySentenceRepository.findById(empty.getId()).orElseThrow().getTotalPlayers())
        .isZero();
    var date = yesterday.minusDays(1);
    var sentence = testAuthHelper.createUsedSentence("누락 과거", date);
    var member = testAuthHelper.createMember();
    var session = seed(member, sentence, "90", 2, clock.instant());
    transactionTemplate.executeWithoutResult(
        status -> {
          dailySentenceRepository.findById(sentence.getId()).orElseThrow().recordTotalPlayers(12);
        });
    assertThat(dailySentenceRepository.findDatesWithMissingFinalRank(LocalDate.now(clock)))
        .contains(date)
        .doesNotContain(yesterday);
    rankingFinalizer.finalizeForDate(date);
    assertThat(dailySentenceRepository.findById(sentence.getId()).orElseThrow().getTotalPlayers())
        .isEqualTo(12);
    assertThat(gameSessionRepository.findById(session.getId()).orElseThrow().getFinalRank())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("복구 한 주기는 가장 오래된 미완료 날짜 7개만 확정합니다")
  void 가장_오래된_7일_복구() {
    LocalDate today = LocalDate.now(clock);
    for (int daysAgo = 1; daysAgo <= 8; daysAgo++) {
      testAuthHelper.createUsedSentence("미완료 " + daysAgo, today.minusDays(daysAgo));
    }

    rankingRecoveryScheduler.recover();

    assertThat(dailySentenceRepository.findDatesWithMissingFinalRank(today))
        .containsExactly(today.minusDays(1));
    for (int daysAgo = 2; daysAgo <= 8; daysAgo++) {
      assertThat(
              dailySentenceRepository
                  .findByUsedAt(today.minusDays(daysAgo))
                  .orElseThrow()
                  .getTotalPlayers())
          .isZero();
    }
  }

  @Test
  @DisplayName("게임 변경 롤백은 세대를 올리지 않고 조정 잠금을 해제합니다")
  void 롤백_세대와_잠금() throws Exception {
    long revision = rankingWriteCoordinator.revision();

    assertThatThrownBy(
            () ->
                rankingWriteCoordinator.write(
                    transactionTemplate,
                    status -> {
                      testAuthHelper.createTodaySentence("롤백 문장");
                      throw new IllegalStateException("rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(rankingWriteCoordinator.revision()).isEqualTo(revision);
    assertThat(dailySentenceRepository.findByUsedAt(LocalDate.now(clock))).isEmpty();
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      executor
          .submit(
              () -> {
                rankingWriteCoordinator.lock();
                try {
                  assertThat(rankingWriteCoordinator.revision()).isEqualTo(revision);
                } finally {
                  rankingWriteCoordinator.unlock();
                }
              })
          .get(5, SECONDS);
    }
  }

  @Test
  @DisplayName("늦은 닉네임 콜백은 최신 DB 회원 정보를 사용합니다")
  void 오래된_회원_콜백_무시() {
    var sentence = testAuthHelper.createTodaySentence("회원 정보 문장");
    var member = testAuthHelper.createMember();
    seed(member, sentence, "90", 2, clock.instant());
    transactionTemplate.executeWithoutResult(
        status -> {
          member.setNickname("최신닉네임");
          // Fixture DB 변경은 실제 서비스 콜백 전에 완료합니다.
          memberRepository.save(member);
        });
    memberRedisSyncer.updateNickname(member.getPublicId(), "이전닉네임");
    assertThat(rankingService.getRankings().rankings().getFirst().nickname()).isEqualTo("최신닉네임");
  }

  private GameSession seed(
      Member member, DailySentence sentence, String best, int attempts, Instant achieved) {
    return transactionTemplate.execute(
        status -> {
          var session = new GameSession(member, sentence);
          for (int i = 0; i < attempts; i++) {
            session.incrementAttempt();
          }
          session.updateBestSimilarity(new BigDecimal(best));
          session.recordRankingImprovement(achieved);
          if (best.equals("100")) {
            session.markCleared(achieved);
          }
          return gameSessionRepository.save(session);
        });
  }
}
