package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.common.exception.ErrorBody;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GameSessionStatus;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.dto.GameStatusResponse;
import com.gakkaweo.backend.game.dto.GuessHistoryResponse;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.HintResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@DisplayName("게임 흐름 통합 테스트")
class GameFlowIntegrationTest extends IntegrationTestBase {

  @Autowired GameSessionRepository gameSessionRepository;

  private HttpHeaders authedJson(Member member) {
    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private void submit(UUID sentenceId, String guessText, HttpHeaders headers) {
    restTemplate.exchange(
        url("/daily/guess"),
        HttpMethod.POST,
        new HttpEntity<>(new GuessRequest(sentenceId, guessText), headers),
        GuessResponse.class);
  }

  @Nested
  @DisplayName("오늘 문제 조회")
  class Today {

    @Test
    @DisplayName("문장 존재 - 200 + TodayResponse")
    void 성공_200() {
      DailySentence sentence = testAuthHelper.createTodaySentence("오늘 날씨가 좋다");

      ResponseEntity<TodayResponse> response =
          restTemplate.getForEntity(url("/daily/today"), TodayResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().sentenceId()).isEqualTo(sentence.getPublicId());
      assertThat(response.getBody().hintMask()).isNotBlank();
      assertThat(response.getBody().wordCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("문장 없음 - 404 SENTENCE_NOT_FOUND")
    void 문장없음_404() {
      ResponseEntity<ErrorBody> response =
          restTemplate.getForEntity(url("/daily/today"), ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
    }
  }

  @Nested
  @DisplayName("추측 제출")
  class Guess {

    @Test
    @DisplayName("익명 추측 - 200 + attemptNumber=null")
    void 익명_200() {
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
      testSimilarityClient.program("안녕", new BigDecimal("50.0"));

      ResponseEntity<GuessResponse> response =
          restTemplate.postForEntity(
              url("/daily/guess"),
              new GuessRequest(sentence.getPublicId(), "안녕"),
              GuessResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().attemptNumber()).isNull();
      assertThat(response.getBody().gameStatus()).isNull();
      assertThat(response.getBody().similarity()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("인증 추측 - 세션 생성 + bestSimilarity 갱신")
    void 인증_세션생성() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
      testSimilarityClient.setDefaultScore(new BigDecimal("60.0"));

      HttpHeaders headers = authedJson(member);

      ResponseEntity<GuessResponse> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "안녕"), headers),
              GuessResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().attemptNumber()).isEqualTo(1);
      assertThat(response.getBody().gameStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("100% 달성 - CLEARED + clearedAt 기록")
    void 정답_CLEARED() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
      testSimilarityClient.program("안녕하세요", new BigDecimal("100.0"));

      HttpHeaders headers = authedJson(member);

      ResponseEntity<GuessResponse> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "안녕하세요"), headers),
              GuessResponse.class);

      assertThat(response.getBody().isCorrect()).isTrue();
      assertThat(response.getBody().gameStatus()).isEqualTo("CLEARED");

      GameSession session =
          gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
      assertThat(session.getStatus()).isEqualTo(GameSessionStatus.CLEARED);
      assertThat(session.getClearedAt()).isNotNull();
    }

    @Test
    @DisplayName("bestSimilarity - 다회 추측 중 최댓값 유지")
    void 베스트유사도_유지() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘이 파랗다");
      testSimilarityClient.program("하늘", new BigDecimal("40.0"));
      testSimilarityClient.program("파랑", new BigDecimal("70.0"));
      testSimilarityClient.program("구름", new BigDecimal("30.0"));

      HttpHeaders headers = authedJson(member);
      submit(sentence.getPublicId(), "하늘", headers);
      submit(sentence.getPublicId(), "파랑", headers);
      submit(sentence.getPublicId(), "구름", headers);

      GameSession session =
          gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
      assertThat(session.getBestSimilarity()).isEqualByComparingTo("70.0");
      assertThat(session.getAttemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("SENTENCE_NOT_FOUND - 존재하지 않는 sentenceId")
    void 미존재_sentence_404() {
      testAuthHelper.createTodaySentence("안녕하세요");
      Member member = testAuthHelper.createMember();

      HttpHeaders headers = authedJson(member);

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(UUID.randomUUID(), "안녕"), headers),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().code()).isEqualTo("SENTENCE_NOT_FOUND");
    }

    @Test
    @DisplayName("AI 서비스 장애 - 503 AI_SERVICE_UNAVAILABLE")
    void AI_장애_503() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
      testSimilarityClient.throwOnNext();

      HttpHeaders headers = authedJson(member);

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "안녕"), headers),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(response.getBody().code()).isEqualTo("AI_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("INVALID_GUESS_TEXT - 특수문자만 입력 (정규화 후 빈 문자열)")
    void 유효하지않은추측_400() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

      HttpHeaders headers = authedJson(member);

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "!!!"), headers),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("INVALID_GUESS_TEXT");
    }

    @Test
    @DisplayName("INVALID_GUESS_TEXT - 미완성 한글 입력 (정규화 후 1글자)")
    void 미완성한글_400() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");

      HttpHeaders headers = authedJson(member);

      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/guess"),
              HttpMethod.POST,
              new HttpEntity<>(new GuessRequest(sentence.getPublicId(), "ㄱ디"), headers),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().code()).isEqualTo("INVALID_GUESS_TEXT");
    }
  }

  @Nested
  @DisplayName("히스토리 / 힌트 / 상태")
  class Views {

    @Test
    @DisplayName("히스토리 - 시도 내역 순서대로")
    void 히스토리_조회() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘");
      testSimilarityClient.setDefaultScore(new BigDecimal("30.0"));

      HttpHeaders headers = authedJson(member);
      submit(sentence.getPublicId(), "구름", headers);
      submit(sentence.getPublicId(), "바람", headers);

      HttpHeaders cookieOnly = testAuthHelper.cookieHeaderFor(member);
      ResponseEntity<GuessHistoryResponse> response =
          restTemplate.exchange(
              url("/daily/history?sentenceId=" + sentence.getPublicId()),
              HttpMethod.GET,
              new HttpEntity<>(cookieOnly),
              GuessHistoryResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().guesses()).hasSize(2);
    }

    @Test
    @DisplayName("힌트 - 60% 미만 403 HINT_NOT_AVAILABLE")
    void 힌트_미달_403() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘");
      testSimilarityClient.setDefaultScore(new BigDecimal("50.0"));

      HttpHeaders headers = authedJson(member);
      submit(sentence.getPublicId(), "구름", headers);

      HttpHeaders cookieOnly = testAuthHelper.cookieHeaderFor(member);
      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/hints?sentenceId=" + sentence.getPublicId()),
              HttpMethod.GET,
              new HttpEntity<>(cookieOnly),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody().code()).isEqualTo("HINT_NOT_AVAILABLE");
    }

    @Test
    @DisplayName("힌트 - 60% 이상 200 (상대 추측은 비어 있을 수 있음)")
    void 힌트_가능_200() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘");
      testSimilarityClient.setDefaultScore(new BigDecimal("70.0"));

      HttpHeaders headers = authedJson(member);
      submit(sentence.getPublicId(), "구름", headers);

      HttpHeaders cookieOnly = testAuthHelper.cookieHeaderFor(member);
      ResponseEntity<HintResponse> response =
          restTemplate.exchange(
              url("/daily/hints?sentenceId=" + sentence.getPublicId()),
              HttpMethod.GET,
              new HttpEntity<>(cookieOnly),
              HintResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().hints()).isNotNull();
    }

    @Test
    @DisplayName("힌트 - 세션 없음 404 SESSION_NOT_FOUND")
    void 힌트_세션없음_404() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘");

      HttpHeaders cookieOnly = testAuthHelper.cookieHeaderFor(member);
      ResponseEntity<ErrorBody> response =
          restTemplate.exchange(
              url("/daily/hints?sentenceId=" + sentence.getPublicId()),
              HttpMethod.GET,
              new HttpEntity<>(cookieOnly),
              ErrorBody.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(response.getBody().code()).isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("상태 조회 - 세션 없으면 null 상태")
    void 상태_세션없음() {
      Member member = testAuthHelper.createMember();
      DailySentence sentence = testAuthHelper.createTodaySentence("하늘");

      HttpHeaders cookieOnly = testAuthHelper.cookieHeaderFor(member);
      ResponseEntity<GameStatusResponse> response =
          restTemplate.exchange(
              url("/daily/status"),
              HttpMethod.GET,
              new HttpEntity<>(cookieOnly),
              GameStatusResponse.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().sentenceId()).isEqualTo(sentence.getPublicId());
      assertThat(response.getBody().gameStatus()).isNull();
    }
  }
}
