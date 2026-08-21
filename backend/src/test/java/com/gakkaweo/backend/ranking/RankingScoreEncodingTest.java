package com.gakkaweo.backend.ranking;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.redis.RedisKeyConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("랭킹 스코어 인코딩 통합 테스트")
class RankingScoreEncodingTest extends IntegrationTestBase {

  @Autowired RankingService rankingService;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired Clock clock;
  @Autowired StringRedisTemplate redisTemplate;

  @Test
  @DisplayName("100% 달성자가 99%보다 항상 상위")
  void 정답_우선() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    Member perfect = testAuthHelper.createMember();
    Member near = testAuthHelper.createMember();

    GameSession perfectSession =
        prepareSession(perfect, sentence, new BigDecimal("100.0"), 10, true);
    GameSession nearSession = prepareSession(near, sentence, new BigDecimal("99.9"), 1, false);

    rankingService.updateRanking(nearSession, near);
    rankingService.updateRanking(perfectSession, perfect);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings).hasSize(2);
    assertThat(rankings.get(0).publicId()).isEqualTo(perfect.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(near.getPublicId());
  }

  @Test
  @DisplayName("100% 간 선착순 - 먼저 clear한 사용자가 상위")
  void 선착순() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    TestClock testClock = (TestClock) clock;
    Member first = testAuthHelper.createMember();
    GameSession firstSession = prepareSession(first, sentence, new BigDecimal("100.0"), 3, true);
    rankingService.updateRanking(firstSession, first);

    testClock.advanceBy(Duration.ofMinutes(5));
    Member second = testAuthHelper.createMember();
    GameSession secondSession = prepareSession(second, sentence, new BigDecimal("100.0"), 5, true);
    rankingService.updateRanking(secondSession, second);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(first.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(second.getPublicId());
  }

  @Test
  @DisplayName("95~99.9% - 유사도 > 시도 횟수 우선순위")
  void 유사도_시도() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    Member high = testAuthHelper.createMember();
    Member low = testAuthHelper.createMember();

    GameSession highSession = prepareSession(high, sentence, new BigDecimal("99.0"), 20, false);
    GameSession lowSession = prepareSession(low, sentence, new BigDecimal("95.0"), 3, false);

    rankingService.updateRanking(lowSession, low);
    rankingService.updateRanking(highSession, high);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(high.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(low.getPublicId());
  }

  @Test
  @DisplayName("시도 횟수가 매우 많아도 유사도가 높은 쪽이 상위 - 유사도 밴드 침범 방지")
  void 시도횟수_밴드침범_방지() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    Member highSim = testAuthHelper.createMember();
    Member lowSim = testAuthHelper.createMember();

    GameSession highSimSession =
        prepareSession(highSim, sentence, new BigDecimal("87.7"), 20_000, false);
    GameSession lowSimSession = prepareSession(lowSim, sentence, new BigDecimal("87.6"), 1, false);

    rankingService.updateRanking(lowSimSession, lowSim);
    rankingService.updateRanking(highSimSession, highSim);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(highSim.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(lowSim.getPublicId());
  }

  @Test
  @DisplayName("100% 간 선착순 - 같은 초 내 밀리초 차이도 먼저 clear한 사용자가 상위")
  void 선착순_밀리초() {
    TestClock testClock = (TestClock) clock;
    testClock.setInstant(LocalDate.now(testClock).atTime(12, 0).atZone(KST).toInstant());
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    Member first = testAuthHelper.createMember();
    GameSession firstSession = prepareSession(first, sentence, new BigDecimal("100.0"), 5, true);
    rankingService.updateRanking(firstSession, first);

    testClock.advanceBy(Duration.ofMillis(500));
    Member second = testAuthHelper.createMember();
    GameSession secondSession = prepareSession(second, sentence, new BigDecimal("100.0"), 3, true);
    rankingService.updateRanking(secondSession, second);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(first.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(second.getPublicId());

    String rankingKey = RedisKeyConstants.rankingKey(LocalDate.now(clock));
    Double firstScore =
        redisTemplate
            .opsForZSet()
            .score(rankingKey, RedisKeyConstants.memberKey(first.getPublicId()));
    Double secondScore =
        redisTemplate
            .opsForZSet()
            .score(rankingKey, RedisKeyConstants.memberKey(second.getPublicId()));
    assertThat(firstScore - secondScore).isEqualTo(500.0);
  }

  @Test
  @DisplayName("999회 초과 동일 유사도 - 시도 횟수 동률로 경과 시간이 빠른 쪽이 상위")
  void 상한초과_경과시간_갈림() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    TestClock testClock = (TestClock) clock;
    Member early = testAuthHelper.createMember();
    GameSession earlySession =
        prepareSession(early, sentence, new BigDecimal("90.0"), 2_000, false);
    rankingService.updateRanking(earlySession, early);

    testClock.advanceBy(Duration.ofMinutes(1));
    Member late = testAuthHelper.createMember();
    GameSession lateSession = prepareSession(late, sentence, new BigDecimal("90.0"), 1_500, false);
    rankingService.updateRanking(lateSession, late);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(early.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(late.getPublicId());
  }

  @Test
  @DisplayName("동일 유사도 - 시도 횟수가 적은 쪽이 상위")
  void 동일유사도_시도() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

    Member few = testAuthHelper.createMember();
    Member many = testAuthHelper.createMember();

    GameSession fewSession = prepareSession(few, sentence, new BigDecimal("90.0"), 3, false);
    GameSession manySession = prepareSession(many, sentence, new BigDecimal("90.0"), 10, false);

    rankingService.updateRanking(manySession, many);
    rankingService.updateRanking(fewSession, few);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    List<RankingResponse.RankingEntry> rankings = response.getBody().rankings();
    assertThat(rankings.get(0).publicId()).isEqualTo(few.getPublicId());
    assertThat(rankings.get(1).publicId()).isEqualTo(many.getPublicId());
  }

  private GameSession prepareSession(
      Member member, DailySentence sentence, BigDecimal similarity, int attempts, boolean cleared) {
    return transactionTemplate.execute(
        status -> {
          GameSession session = new GameSession(member, sentence);
          session.updateBestSimilarity(similarity);
          for (int i = 0; i < attempts; i++) {
            session.incrementAttempt();
          }
          if (cleared) {
            session.markCleared(clock.instant());
          }
          return gameSessionRepository.save(session);
        });
  }
}
