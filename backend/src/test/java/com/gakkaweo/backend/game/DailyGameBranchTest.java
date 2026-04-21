package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GameSessionStatus;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.support.IntegrationTestBase;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("DailyGameService 분기 커버 보강")
class DailyGameBranchTest extends IntegrationTestBase {

  @Autowired Clock clock;
  @Autowired DailySentenceRepository dailySentenceRepository;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired TransactionTemplate transactionTemplate;
  @Autowired EntityManager entityManager;

  @Test
  @DisplayName("오늘 조회 - 어제 문장 존재 시 yesterdaySentence/yesterdayUsedAt 반환")
  void 오늘_어제문장_포함() {
    testAuthHelper.createTodaySentence("오늘 문장");
    createYesterdaySentence("어제 문장");

    ResponseEntity<TodayResponse> response =
        restTemplate.getForEntity(url("/daily/today"), TodayResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().yesterdaySentence()).isEqualTo("어제 문장");
    assertThat(response.getBody().yesterdayDate()).isEqualTo(LocalDate.now(clock).minusDays(1));
  }

  @Test
  @DisplayName("익명 추측 - 100% 정답 시 isCorrect=true")
  void 익명_100퍼센트_isCorrect_true() {
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕");
    testSimilarityClient.program("안녕", new BigDecimal("100.0"));

    ResponseEntity<GuessResponse> response =
        restTemplate.postForEntity(
            url("/daily/guess"),
            new GuessRequest(sentence.getPublicId(), "안녕"),
            GuessResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().isCorrect()).isTrue();
  }

  @Test
  @DisplayName("CLEARED 세션에서 100% 재추측 - updateClearedAt 호출되지만 bestSimilarity=100이면 변경 안 됨")
  void cleared_세션_100퍼센트_재추측() {
    Member member = testAuthHelper.createMember();
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
    testSimilarityClient.program("안녕하세요", new BigDecimal("100.0"));
    HttpHeaders headers = authedJson(member);

    submit(sentence, "안녕하세요", headers);
    GameSession first =
        gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    assertThat(first.getStatus()).isEqualTo(GameSessionStatus.CLEARED);

    submit(sentence, "안녕하세요", headers);
    GameSession second =
        gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    assertThat(second.getStatus()).isEqualTo(GameSessionStatus.CLEARED);
    assertThat(second.getClearedAt()).isEqualTo(first.getClearedAt());
  }

  @Test
  @DisplayName("CLEARED 세션에서 100% 미만 재추측 - clearedAt/status 유지")
  void cleared_세션_미만_재추측() {
    Member member = testAuthHelper.createMember();
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
    testSimilarityClient.program("안녕하세요", new BigDecimal("100.0"));
    testSimilarityClient.program("안녕", new BigDecimal("50.0"));
    HttpHeaders headers = authedJson(member);

    submit(sentence, "안녕하세요", headers);
    GameSession first =
        gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();

    submit(sentence, "안녕", headers);
    GameSession second =
        gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    assertThat(second.getStatus()).isEqualTo(GameSessionStatus.CLEARED);
    assertThat(second.getClearedAt()).isEqualTo(first.getClearedAt());
    assertThat(second.getBestSimilarity()).isEqualByComparingTo("100.0");
  }

  @Test
  @DisplayName("오늘이 아닌 sentence publicId - 404 SENTENCE_NOT_FOUND")
  void 오늘아닌_sentence_404() {
    testAuthHelper.createTodaySentence("오늘 문장");
    DailySentence yesterday = createYesterdaySentence("어제 문장");
    Member member = testAuthHelper.createMember();
    HttpHeaders headers = authedJson(member);

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/daily/guess"),
            HttpMethod.POST,
            new HttpEntity<>(new GuessRequest(yesterday.getPublicId(), "추측"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
  }

  @Test
  @DisplayName("EXPIRED 세션 추측 - 409 GAME_EXPIRED")
  void expired_세션_409() {
    Member member = testAuthHelper.createMember();
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
    testSimilarityClient.setDefaultScore(new BigDecimal("50.0"));
    HttpHeaders headers = authedJson(member);

    submit(sentence, "추측", headers);
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createQuery(
                    "UPDATE GameSession s SET s.status ="
                        + " com.gakkaweo.backend.domain.game.entity.GameSessionStatus.EXPIRED,"
                        + " s.version = s.version + 1 WHERE s.member.id = :mid")
                .setParameter("mid", member.getId())
                .executeUpdate());

    ResponseEntity<ErrorBody> response =
        restTemplate.exchange(
            url("/daily/guess"),
            HttpMethod.POST,
            new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "추측2"), headers),
            ErrorBody.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("GAME_EXPIRED");
  }

  private DailySentence createYesterdaySentence(String text) {
    return transactionTemplate.execute(
        status -> {
          DailySentence sentence = new DailySentence(text);
          sentence.setUsedAt(LocalDate.now(clock).minusDays(1));
          sentence.setStatus(DailySentenceStatus.USED);
          return dailySentenceRepository.save(sentence);
        });
  }

  private void submit(DailySentence sentence, String guessText, HttpHeaders headers) {
    ResponseEntity<GuessResponse> response =
        restTemplate.exchange(
            url("/daily/guess"),
            HttpMethod.POST,
            new HttpEntity<>(new GuessRequest(sentence.getPublicId(), guessText), headers),
            GuessResponse.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
  }

  private HttpHeaders authedJson(Member member) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
