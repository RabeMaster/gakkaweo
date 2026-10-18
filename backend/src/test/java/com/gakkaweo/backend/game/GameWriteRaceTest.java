package com.gakkaweo.backend.game;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gakkaweo.backend.admin.dto.EmergencyReplaceRequest;
import com.gakkaweo.backend.admin.service.AdminSentenceService;
import com.gakkaweo.backend.auth.dto.TokenPair;
import com.gakkaweo.backend.auth.service.AccountService;
import com.gakkaweo.backend.auth.service.AuthSessionService;
import com.gakkaweo.backend.domain.auth.repository.AuthSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.service.DailyGameService;
import com.gakkaweo.backend.ranking.service.RankingWriteCoordinator;
import com.gakkaweo.backend.support.IntegrationTestBase;
import com.gakkaweo.backend.support.TestClock;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("AI 처리와 게임·회원 변경 병렬 통합 테스트")
class GameWriteRaceTest extends IntegrationTestBase {

  @Autowired DailyGameService gameService;
  @Autowired AccountService accountService;
  @Autowired AuthSessionService authSessionService;
  @Autowired AdminSentenceService adminSentenceService;
  @Autowired GameSessionRepository gameSessionRepository;
  @Autowired GuessHistoryRepository guessHistoryRepository;
  @Autowired RankingWriteCoordinator rankingWriteCoordinator;
  @Autowired AuthSessionRepository authSessionRepository;
  @Autowired MemberRepository memberRepository;
  @Autowired TransactionTemplate transactionTemplate;

  private UUID testMemberId;
  private Long testMemberNumericId;
  private TokenPair testTokens;

  @Test
  @DisplayName("낮거나 같은 점수는 랭킹 세대를 유지하고 최고 기록 개선만 세대를 올립니다")
  void 최고_기록_개선만_랭킹_세대_변경() {
    var sentence = testAuthHelper.createTodaySentence("최고 기록 세대 문장");
    var member = testAuthHelper.createMember();
    testSimilarityClient.setDefaultScore(new BigDecimal("90.0"));
    gameService.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "최초 최고 추측"), member.getPublicId());
    long revision = rankingWriteCoordinator.revision();
    var first = gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();

    testSimilarityClient.setDefaultScore(new BigDecimal("50.0"));
    gameService.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "낮은 후속 추측"), member.getPublicId());
    testSimilarityClient.setDefaultScore(new BigDecimal("90.0"));
    gameService.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "같은 후속 추측"), member.getPublicId());

    var unchanged = gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    assertThat(rankingWriteCoordinator.revision()).isEqualTo(revision);
    assertThat(unchanged.getAttemptCount()).isEqualTo(3);
    assertThat(unchanged.getRankingAttemptCount()).isEqualTo(1);
    assertThat(unchanged.getRankingAchievedAt()).isEqualTo(first.getRankingAchievedAt());
    assertThat(guessHistoryRepository.count()).isEqualTo(3);

    testSimilarityClient.setDefaultScore(new BigDecimal("95.0"));
    gameService.guessAuthenticated(
        new GuessRequest(sentence.getPublicId(), "개선된 최고 추측"), member.getPublicId());

    var improved = gameSessionRepository.findByMemberAndSentence(member, sentence).orElseThrow();
    assertThat(rankingWriteCoordinator.revision()).isEqualTo(revision + 1);
    assertThat(improved.getBestSimilarity()).isEqualByComparingTo("95.0");
    assertThat(improved.getRankingAttemptCount()).isEqualTo(4);
    assertThat(guessHistoryRepository.count()).isEqualTo(4);
  }

  @Test
  @DisplayName("AI 실패는 빈 게임 세션을 남기지 않습니다")
  void AI_실패_빈세션_없음() {
    var sentence = testAuthHelper.createTodaySentence("AI 실패 문장");
    var member = testAuthHelper.createMember();
    testSimilarityClient.throwOnNext();

    assertThatThrownBy(
            () ->
                gameService.guessAuthenticated(
                    new GuessRequest(sentence.getPublicId(), "실패 추측"), member.getPublicId()))
        .isInstanceOf(com.gakkaweo.backend.common.exception.BusinessException.class);
    assertThat(gameSessionRepository.count()).isZero();
  }

  @Test
  @DisplayName("AI 계산 중 회원 삭제는 늦은 세션 저장을 차단합니다")
  void AI_중_탈퇴() throws Exception {
    runDelayed(false, () -> accountService.deleteAccount(testMemberId), false);
  }

  @Test
  @DisplayName("AI 계산 중 자정이 지나면 인증·익명 추측 모두 저장하지 않습니다")
  void AI_중_자정() throws Exception {
    runDelayed(false, () -> ((TestClock) clock).advanceBy(Duration.ofDays(1)), false);
    databaseCleaner.clean();
    ((TestClock) clock).setInstant(java.time.Clock.system(KST).instant());
    runDelayed(true, () -> ((TestClock) clock).advanceBy(Duration.ofDays(1)), false);
  }

  @Test
  @DisplayName("AI 계산 중 긴급 문제 교체는 늦은 저장을 차단합니다")
  void AI_중_교체() throws Exception {
    runDelayed(
        false,
        () -> {
          var next = testAuthHelper.createActiveSentence("교체 후 문장");
          var admin = testAuthHelper.createAdmin();
          adminSentenceService.emergencyReplace(
              new EmergencyReplaceRequest(next.getPublicId(), true),
              admin.getPublicId(),
              "127.0.0.1");
        },
        false);
  }

  @Test
  @DisplayName("인증 갱신·폐기·만료 정리는 게임 전역 잠금 없이 완료됩니다")
  void AI_중_인증_정리() throws Exception {
    runDelayed(
        false,
        () -> {
          assertThat(authSessionService.rotate(testTokens.refreshToken())).isNotNull();
          authSessionService.revokeAll(testMemberNumericId);
          transactionTemplate.executeWithoutResult(
              status -> {
                memberRepository.findByIdForUpdate(testMemberNumericId).orElseThrow();
                authSessionRepository
                    .findByMemberId(testMemberNumericId)
                    .forEach(session -> session.extend(clock.instant().minusSeconds(1)));
              });
          authSessionService.discardExpired();
          assertThat(authSessionRepository.findByMemberId(testMemberNumericId)).isEmpty();
        },
        true);
  }

  private void runDelayed(boolean anonymous, Runnable change, boolean success) throws Exception {
    var sentence = testAuthHelper.createTodaySentence("병렬 문장");
    var member = testAuthHelper.createMember();
    testMemberId = member.getPublicId();
    testMemberNumericId = member.getId();
    if (success) {
      testTokens = authSessionService.issue(member.getId());
    }
    var entered = new CountDownLatch(1);
    var proceed = new CountDownLatch(1);
    testSimilarityClient.delayNext(entered, proceed);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var guess =
          executor.submit(
              () ->
                  anonymous
                      ? gameService.guessAnonymous(
                          new GuessRequest(sentence.getPublicId(), "지연 추측"))
                      : gameService.guessAuthenticated(
                          new GuessRequest(sentence.getPublicId(), "지연 추측"), member.getPublicId()));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(gameSessionRepository.count()).isZero();
      if (success) {
        rankingWriteCoordinator.lock();
        try {
          executor.submit(change).get(5, TimeUnit.SECONDS);
        } finally {
          rankingWriteCoordinator.unlock();
        }
      } else {
        executor.submit(change).get(5, TimeUnit.SECONDS);
      }
      proceed.countDown();
      if (success) {
        assertThat(guess.get(10, TimeUnit.SECONDS).similarity())
            .isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(guessHistoryRepository.count()).isEqualTo(1);
      } else {
        assertThatThrownBy(() -> guess.get(10, TimeUnit.SECONDS))
            .hasCauseInstanceOf(com.gakkaweo.backend.common.exception.BusinessException.class);
        assertThat(gameSessionRepository.count()).isZero();
        assertThat(guessHistoryRepository.count()).isZero();
      }
    } finally {
      proceed.countDown();
    }
  }
}
