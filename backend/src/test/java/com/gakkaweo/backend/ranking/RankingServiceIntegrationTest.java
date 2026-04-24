package com.gakkaweo.backend.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.admin.dto.FullRankingResponse;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("RankingService 통합 테스트 (Redis 데이터 포함 경로)")
class RankingServiceIntegrationTest extends IntegrationTestBase {

  @Autowired RankingService rankingService;
  @Autowired MemberRepository memberRepository;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired Clock rankingClock;

  @SuppressWarnings("unused")
  private static List<String> nicknames(RankingResponse response) {
    List<String> result = new ArrayList<>();
    for (RankingResponse.RankingEntry e : response.rankings()) {
      result.add(e.nickname());
    }
    return result;
  }

  @Test
  @DisplayName("GET /ranking/today 미인증 - getRankings 루프 + profileUrl null/empty/값 3분기")
  void 랭킹_조회_미인증() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    Member withProfile =
        seedRanking(sentence, "프로필유저", "/uploads/user.webp", new BigDecimal("100.0"), true);
    seedRanking(sentence, "빈프로필", "", new BigDecimal("95.0"), false);
    seedRanking(sentence, "노프로필", null, new BigDecimal("70.0"), false);

    ResponseEntity<RankingResponse> response =
        restTemplate.getForEntity(url("/ranking/today"), RankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalPlayers()).isEqualTo(3L);
    assertThat(response.getBody().rankings()).hasSize(3);
    assertThat(response.getBody().rankings())
        .extracting(RankingResponse.RankingEntry::nickname)
        .containsExactly("프로필유저", "빈프로필", "노프로필");
    assertThat(response.getBody().rankings().get(0).profileUrl()).isEqualTo("/uploads/user.webp");
    assertThat(response.getBody().rankings().get(1).profileUrl()).isNull();
    assertThat(response.getBody().rankings().get(2).profileUrl()).isNull();
    assertThat(response.getBody().myRank()).isNull();
    assertThat(withProfile.getNickname()).isEqualTo("프로필유저");
  }

  @Test
  @DisplayName("GET /ranking/today 인증 - getRankingsForUser + lookupMyRank")
  void 랭킹_조회_인증_myRank() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    Member top = seedRanking(sentence, "1등", null, new BigDecimal("100.0"), true);
    Member me = seedRanking(sentence, "나", null, new BigDecimal("80.0"), false);

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(me);
    ResponseEntity<RankingResponse> response =
        restTemplate.exchange(
            url("/ranking/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            RankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().myRank()).isNotNull();
    assertThat(response.getBody().myRank().rank()).isEqualTo(2);
    assertThat(response.getBody().myRank().similarity()).isEqualByComparingTo("80.0");
    assertThat(top.getNickname()).isEqualTo("1등");
  }

  @Test
  @DisplayName("GET /ranking/today 인증 - 랭킹 없는 사용자는 myRank null")
  void 랭킹_조회_인증_myRank_없음() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    seedRanking(sentence, "1등", null, new BigDecimal("100.0"), true);
    Member outsider = testAuthHelper.createMember();

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(outsider);
    ResponseEntity<RankingResponse> response =
        restTemplate.exchange(
            url("/ranking/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            RankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().myRank()).isNull();
  }

  @Test
  @DisplayName(
      "GET /ranking/today 인증 - 어제 세션 + finalRank 세팅 시 yesterdayRank/yesterdayTotalPlayers 반환")
  void 랭킹_조회_어제기록() {
    DailySentence todaySentence = testAuthHelper.createTodaySentence("오늘 문장");
    Member me = seedRanking(todaySentence, "나", null, new BigDecimal("80.0"), false);

    LocalDate yesterday = LocalDate.now(rankingClock).minusDays(1);
    transactionTemplate.executeWithoutResult(
        status -> {
          DailySentence yesterdaySentence = new DailySentence("어제 문장");
          yesterdaySentence.setUsedAt(yesterday);
          yesterdaySentence.setStatus(DailySentenceStatus.USED);
          yesterdaySentence.recordTotalPlayers(5);
          dailySentenceRepository.save(yesterdaySentence);

          GameSession yesterdaySession = new GameSession(me, yesterdaySentence);
          yesterdaySession.updateBestSimilarity(new BigDecimal("90.0"));
          yesterdaySession.recordFinalRank(3);
          gameSessionRepository.save(yesterdaySession);
        });

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(me);
    ResponseEntity<RankingResponse> response =
        restTemplate.exchange(
            url("/ranking/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            RankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().yesterdayRank()).isEqualTo(3);
    assertThat(response.getBody().yesterdayTotalPlayers()).isEqualTo(5);
  }

  @Test
  @DisplayName("GET /ranking/today 인증 - 어제 문장은 있지만 내 세션이 없으면 yesterdayRank null / totalPlayers만 반환")
  void 랭킹_조회_어제문장만() {
    DailySentence todaySentence = testAuthHelper.createTodaySentence("오늘 문장");
    Member me = seedRanking(todaySentence, "나", null, new BigDecimal("80.0"), false);

    LocalDate yesterday = LocalDate.now(rankingClock).minusDays(1);
    transactionTemplate.executeWithoutResult(
        status -> {
          DailySentence yesterdaySentence = new DailySentence("어제 문장");
          yesterdaySentence.setUsedAt(yesterday);
          yesterdaySentence.setStatus(DailySentenceStatus.USED);
          yesterdaySentence.recordTotalPlayers(10);
          dailySentenceRepository.save(yesterdaySentence);
        });

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(me);
    ResponseEntity<RankingResponse> response =
        restTemplate.exchange(
            url("/ranking/today"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            RankingResponse.class);

    assertThat(response.getBody().yesterdayTotalPlayers()).isEqualTo(10);
    assertThat(response.getBody().yesterdayRank()).isNull();
  }

  @Test
  @DisplayName("GET /admin/dashboard/ranking - getFullRankingsForDate 전체 랭킹 반환")
  void 어드민_전체랭킹() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    seedRanking(sentence, "1등", "/uploads/p1.webp", new BigDecimal("100.0"), true);
    seedRanking(sentence, "2등", null, new BigDecimal("95.0"), false);

    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<FullRankingResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/ranking"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            FullRankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalPlayers()).isEqualTo(2L);
    assertThat(response.getBody().rankings())
        .extracting(FullRankingResponse.RankingEntry::nickname)
        .containsExactly("1등", "2등");
    assertThat(response.getBody().rankings().get(0).profileUrl()).isEqualTo("/uploads/p1.webp");
    assertThat(response.getBody().rankings().get(1).profileUrl()).isNull();
  }

  @Test
  @DisplayName("GET /admin/dashboard/ranking - 랭킹 없는 날짜는 빈 목록")
  void 어드민_전체랭킹_빈날짜() {
    testAuthHelper.createTodaySentence("오늘 문장");
    Member admin = testAuthHelper.createAdmin();
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(admin);

    ResponseEntity<FullRankingResponse> response =
        restTemplate.exchange(
            url("/admin/dashboard/ranking?date=2020-01-01"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            FullRankingResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().rankings()).isEmpty();
  }

  @Test
  @DisplayName("rebuildRankingCache - DB 세션 기반 Redis 재구축")
  void 랭킹캐시_재구축() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    Member m1 = seedRanking(sentence, "재구축1", "/uploads/r1.webp", new BigDecimal("100.0"), true);
    Member m2 = seedRanking(sentence, "재구축2", null, new BigDecimal("80.0"), false);

    LocalDate today = LocalDate.now(rankingClock);
    int rebuilt = rankingService.rebuildRankingCache(today);

    assertThat(rebuilt).isEqualTo(2);
    RankingResponse rankings = rankingService.getRankings();
    assertThat(rankings.totalPlayers()).isEqualTo(2L);
    assertThat(rankings.rankings())
        .extracting(RankingResponse.RankingEntry::nickname)
        .containsExactly("재구축1", "재구축2");
    assertThat(m1.getNickname()).isEqualTo("재구축1");
    assertThat(m2.getNickname()).isEqualTo("재구축2");
  }

  @Test
  @DisplayName("rebuildRankingCache - 오늘 문장 없으면 SENTENCE_NOT_FOUND")
  void 랭킹캐시_재구축_문장없음() {
    LocalDate today = LocalDate.now(rankingClock);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> rankingService.rebuildRankingCache(today))
        .isInstanceOf(com.gakkaweo.backend.common.exception.BusinessException.class);
  }

  @Test
  @DisplayName("expirePreviousDayRankingKeys - TTL 적용")
  void 이전날_키만료() {
    DailySentence sentence = testAuthHelper.createTodaySentence("오늘 문장");
    seedRanking(sentence, "참여자", null, new BigDecimal("80.0"), false);

    LocalDate today = LocalDate.now(rankingClock);
    rankingService.expirePreviousDayRankingKeys(today);
  }

  private Member seedRanking(
      DailySentence sentence,
      String nickname,
      String profileUrl,
      BigDecimal similarity,
      boolean perfect) {
    Member member =
        transactionTemplate.execute(
            status -> {
              Member m = new Member(nickname);
              if (profileUrl != null) {
                m.setProfileUrl(profileUrl);
              }
              memberRepository.save(m);
              GameSession session = new GameSession(m, sentence);
              session.updateBestSimilarity(similarity);
              if (perfect) {
                session.markCleared(rankingClock.instant());
              }
              gameSessionRepository.save(session);
              return m;
            });
    GameSession session =
        gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    rankingService.updateRanking(session, member);
    return member;
  }
}
