package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.dto.YesterdayMeResponse;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("어제 내 기록 조회 (/daily/yesterday/me)")
class DailyGameYesterdayMeTest extends IntegrationTestBase {

  @Autowired Clock clock;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired GuessHistoryRepository guessHistoryRepository;
  @Autowired TransactionTemplate transactionTemplate;

  @Test
  @DisplayName("어제 참여 + finalRank 세팅 - 전체 필드 반환")
  void 어제기록_전체필드() {
    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence = createYesterdaySentence("어제 문장", 12);
    createYesterdaySession(
        member,
        yesterdaySentence,
        session -> {
          session.updateBestSimilarity(new BigDecimal("85.7"));
          session.incrementAttempt();
          session.incrementAttempt();
          session.recordFinalRank(3);
          guessHistoryRepository.save(
              new GuessHistory(session, "낮은 추측", new BigDecimal("40.0"), 1));
          guessHistoryRepository.save(
              new GuessHistory(session, "최고 추측", new BigDecimal("85.7"), 2));
        });

    ResponseEntity<YesterdayMeResponse> response = getYesterdayMe(member);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    YesterdayMeResponse body = response.getBody();
    assertThat(body.yesterdayDate()).isEqualTo(LocalDate.now(clock).minusDays(1));
    assertThat(body.participated()).isTrue();
    assertThat(body.rank()).isEqualTo(3);
    assertThat(body.totalPlayers()).isEqualTo(12);
    assertThat(body.bestGuessText()).isEqualTo("최고 추측");
    assertThat(body.bestSimilarity()).isEqualByComparingTo("85.7");
    assertThat(body.attemptCount()).isEqualTo(2);
    assertThat(body.cleared()).isFalse();
  }

  @Test
  @DisplayName("어제 문장은 있지만 내 세션 없음 - participated=false")
  void 어제기록_미참여() {
    Member member = testAuthHelper.createMember();
    createYesterdaySentence("어제 문장", 10);

    ResponseEntity<YesterdayMeResponse> response = getYesterdayMe(member);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    YesterdayMeResponse body = response.getBody();
    assertThat(body.yesterdayDate()).isEqualTo(LocalDate.now(clock).minusDays(1));
    assertThat(body.participated()).isFalse();
    assertThat(body.rank()).isNull();
    assertThat(body.totalPlayers()).isNull();
    assertThat(body.bestGuessText()).isNull();
    assertThat(body.bestSimilarity()).isNull();
    assertThat(body.attemptCount()).isNull();
    assertThat(body.cleared()).isNull();
  }

  @Test
  @DisplayName("어제 문장 자체가 없음 - participated=false, yesterdayDate=null")
  void 어제기록_문장없음() {
    Member member = testAuthHelper.createMember();

    ResponseEntity<YesterdayMeResponse> response = getYesterdayMe(member);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().yesterdayDate()).isNull();
    assertThat(response.getBody().participated()).isFalse();
  }

  @Test
  @DisplayName("어제 클리어 세션 - cleared=true")
  void 어제기록_클리어() {
    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence = createYesterdaySentence("어제 문장", 8);
    createYesterdaySession(
        member,
        yesterdaySentence,
        session -> {
          session.updateBestSimilarity(new BigDecimal("100.0"));
          session.incrementAttempt();
          session.markCleared(clock.instant());
          guessHistoryRepository.save(
              new GuessHistory(session, "어제 문장", new BigDecimal("100.0"), 1));
        });

    ResponseEntity<YesterdayMeResponse> response = getYesterdayMe(member);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().cleared()).isTrue();
    assertThat(response.getBody().bestSimilarity()).isEqualByComparingTo("100.0");
  }

  @Test
  @DisplayName("동률 유사도 추측 2건 - attemptNumber 낮은 추측 반환")
  void 어제기록_동률_먼저낸추측() {
    Member member = testAuthHelper.createMember();
    DailySentence yesterdaySentence = createYesterdaySentence("어제 문장", 5);
    createYesterdaySession(
        member,
        yesterdaySentence,
        session -> {
          session.updateBestSimilarity(new BigDecimal("70.0"));
          session.incrementAttempt();
          session.incrementAttempt();
          guessHistoryRepository.save(
              new GuessHistory(session, "먼저 낸 추측", new BigDecimal("70.0"), 1));
          guessHistoryRepository.save(
              new GuessHistory(session, "나중 낸 추측", new BigDecimal("70.0"), 2));
        });

    ResponseEntity<YesterdayMeResponse> response = getYesterdayMe(member);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().bestGuessText()).isEqualTo("먼저 낸 추측");
  }

  @Test
  @DisplayName("비인증 요청 - 401")
  void 어제기록_비인증_401() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/daily/yesterday/me"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private ResponseEntity<YesterdayMeResponse> getYesterdayMe(Member member) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    return restTemplate.exchange(
        url("/daily/yesterday/me"),
        HttpMethod.GET,
        new HttpEntity<>(headers),
        YesterdayMeResponse.class);
  }

  private DailySentence createYesterdaySentence(String text, int totalPlayers) {
    return transactionTemplate.execute(
        status -> {
          DailySentence sentence = new DailySentence(text);
          sentence.setUsedAt(LocalDate.now(clock).minusDays(1));
          sentence.setStatus(DailySentenceStatus.USED);
          sentence.recordTotalPlayers(totalPlayers);
          return dailySentenceRepository.save(sentence);
        });
  }

  private void createYesterdaySession(
      Member member, DailySentence sentence, Consumer<GameSession> customizer) {
    transactionTemplate.executeWithoutResult(
        status -> {
          GameSession session = gameSessionRepository.save(new GameSession(member, sentence));
          customizer.accept(session);
        });
  }
}
