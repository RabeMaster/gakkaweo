package com.gakkaweo.backend.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.support.IntegrationTestBase;
import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

@DisplayName("동시성 통합 테스트")
class ConcurrencyTest extends IntegrationTestBase {

  @Autowired GameSessionRepository gameSessionRepository;

  @Test
  @DisplayName("같은 사용자 2 스레드 동시 추측 - 하나의 세션으로 수렴")
  void 동시_추측_단일세션() throws Exception {
    Member member = testAuthHelper.createMember();
    DailySentence sentence = testAuthHelper.createTodaySentence("안녕하세요");
    testSimilarityClient.setDefaultScore(new BigDecimal("30.0"));

    HttpHeaders headers = testAuthHelper.cookieHeaderFor(member);
    headers.setContentType(MediaType.APPLICATION_JSON);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> a =
          executor.submit(() -> submitGuess(sentence, "추측A", headers).getStatusCodeValue());
      Future<Integer> b =
          executor.submit(() -> submitGuess(sentence, "추측B", headers).getStatusCodeValue());

      int statusA = a.get(10, TimeUnit.SECONDS);
      int statusB = b.get(10, TimeUnit.SECONDS);

      // 경쟁 결과: 성공 200, 충돌 409, 드물게 500(낙관적 락/unique 위반 변환 실패)
      // 최소한 하나는 반드시 성공해야 세션이 저장됨
      assertThat(statusA == 200 || statusB == 200).isTrue();
    } finally {
      executor.shutdownNow();
    }

    java.util.Optional<GameSession> session =
        gameSessionRepository.findByMemberAndSentence(member, sentence);
    assertThat(session).isPresent();
  }

  private org.springframework.http.ResponseEntity<String> submitGuess(
      DailySentence sentence, String guessText, HttpHeaders headers) {
    return restTemplate.exchange(
        url("/daily/guess"),
        HttpMethod.POST,
        new HttpEntity<>(new GuessRequest(sentence.getPublicId(), guessText), headers),
        String.class);
  }
}
