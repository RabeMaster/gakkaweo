package com.gakkaweo.backend.game.service;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.game.GameConstants;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.game.repository.HintProjection;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.game.config.GameProperties;
import com.gakkaweo.backend.game.dto.GameStatusResponse;
import com.gakkaweo.backend.game.dto.GuessHistoryResponse;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.HintResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.game.util.HintMaskGenerator;
import com.gakkaweo.backend.infra.ai.service.SimilarityClient;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DailyGameService {

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final GuessHistoryRepository guessHistoryRepository;
  private final MemberRepository memberRepository;
  private final SimilarityClient similarityService;
  private final RankingService rankingService;
  private final HintMaskGenerator hintMaskGenerator;
  private final GameProperties gameProperties;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  private static boolean isPerfect(BigDecimal similarity) {
    return similarity.compareTo(GameConstants.PERFECT_SIMILARITY) >= 0;
  }

  @Transactional(readOnly = true)
  public TodayResponse getToday() {
    DailySentence sentence = findTodaySentence();
    HintMaskGenerator.HintMask hint = hintMaskGenerator.generate(sentence.getSentence());

    Instant expiresAt = LocalDate.now(clock).plusDays(1).atStartOfDay(KST).toInstant();

    LocalDate yesterday = LocalDate.now(clock).minusDays(1);
    String yesterdaySentence =
        dailySentenceRepository
            .findByUsedAt(yesterday)
            .map(DailySentence::getSentence)
            .orElse(null);

    return new TodayResponse(
        sentence.getPublicId(),
        hint.mask(),
        hint.charCounts().size(),
        hint.charCounts(),
        expiresAt,
        yesterdaySentence,
        yesterdaySentence != null ? yesterday : null);
  }

  @Transactional
  public GuessResponse guessAuthenticated(GuessRequest request, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(request.sentenceId());
    Member member = findMember(memberPublicId);

    GameSession session = findOrCreateSession(member, sentence);

    validateGuessAllowed(session);

    BigDecimal similarity =
        similarityService.calculateSimilarity(
            sentence.getId(), request.guessText(), sentence.getSentence(), remainingTtl());

    boolean isCorrect = similarity.compareTo(gameProperties.similarityThreshold()) >= 0;

    BigDecimal previousBest = session.getBestSimilarity();
    Instant now = clock.instant();

    if (session.isInProgress()) {
      session.incrementAttempt();
      if (isCorrect) {
        session.markCleared(now);
      }
    } else if (session.isCleared() && isPerfect(similarity)) {
      session.updateClearedAt(now);
    }
    session.updateBestSimilarity(similarity);

    int guessSequence = (int) guessHistoryRepository.countBySession(session) + 1;

    guessHistoryRepository.save(
        new GuessHistory(session, request.guessText(), similarity, guessSequence));

    gameSessionRepository.save(session);

    if (similarity.compareTo(previousBest) > 0) {
      rankingService.updateRanking(session, member);
      eventPublisher.publishEvent(new RankingUpdateEvent());
    }

    log.info(
        "추측 제출: memberId={}, sentenceId={}, similarity={}, isCorrect={}",
        memberPublicId,
        request.sentenceId(),
        similarity,
        isCorrect);

    return new GuessResponse(
        similarity, session.getAttemptCount(), isCorrect, session.getStatus().name(), now);
  }

  @Transactional(readOnly = true)
  public GuessResponse guessAnonymous(GuessRequest request) {
    DailySentence sentence = findTodaySentenceByPublicId(request.sentenceId());

    BigDecimal similarity =
        similarityService.calculateSimilarity(
            sentence.getId(), request.guessText(), sentence.getSentence(), remainingTtl());

    boolean isCorrect = similarity.compareTo(gameProperties.similarityThreshold()) >= 0;

    return new GuessResponse(similarity, null, isCorrect, null, clock.instant());
  }

  @Transactional(readOnly = true)
  public GuessHistoryResponse getHistory(UUID sentenceId, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(sentenceId);
    Member member = findMember(memberPublicId);

    return gameSessionRepository
        .findByMemberAndSentence(member, sentence)
        .map(
            session -> {
              List<GuessHistory> guesses =
                  guessHistoryRepository.findBySessionOrderByAttemptNumberAsc(session);
              List<GuessHistoryResponse.GuessEntry> entries =
                  guesses.stream()
                      .map(
                          g ->
                              new GuessHistoryResponse.GuessEntry(
                                  g.getGuessText(),
                                  g.getSimilarity(),
                                  g.getAttemptNumber(),
                                  g.getCreatedAt()))
                      .toList();
              return new GuessHistoryResponse(entries);
            })
        .orElse(new GuessHistoryResponse(List.of()));
  }

  @Transactional(readOnly = true)
  public HintResponse getHints(UUID sentenceId, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(sentenceId);
    Member member = findMember(memberPublicId);

    GameSession session =
        gameSessionRepository
            .findByMemberAndSentence(member, sentence)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

    if (session.getBestSimilarity().compareTo(gameProperties.hintTriggerThreshold()) < 0) {
      throw new BusinessException(ErrorCode.HINT_NOT_AVAILABLE);
    }

    BigDecimal maxSimilarity = session.getBestSimilarity().min(gameProperties.hintMaxSimilarity());

    List<HintProjection> projections =
        guessHistoryRepository.findHints(
            sentence.getId(), member.getId(), maxSimilarity, gameProperties.hintMaxCount());

    List<HintResponse.HintEntry> entries =
        projections.stream()
            .map(p -> new HintResponse.HintEntry(p.getGuessText(), p.getSimilarity()))
            .toList();

    return new HintResponse(entries);
  }

  @Transactional(readOnly = true)
  public GameStatusResponse getStatus(UUID memberPublicId) {
    DailySentence sentence = findTodaySentence();
    Member member = findMember(memberPublicId);

    return gameSessionRepository
        .findByMemberAndSentence(member, sentence)
        .map(
            session ->
                new GameStatusResponse(
                    sentence.getPublicId(),
                    session.getStatus().name(),
                    session.getBestSimilarity(),
                    session.getAttemptCount(),
                    session.getClearedAt()))
        .orElse(new GameStatusResponse(sentence.getPublicId(), null, BigDecimal.ZERO, 0, null));
  }

  private GameSession findOrCreateSession(Member member, DailySentence sentence) {
    return gameSessionRepository
        .findByMemberAndSentence(member, sentence)
        .orElseGet(
            () -> {
              try {
                return gameSessionRepository.save(new GameSession(member, sentence));
              } catch (DataIntegrityViolationException e) {
                log.debug(
                    "세션 동시 생성 감지, 기존 세션 재조회: memberId={}, sentenceId={}",
                    member.getPublicId(),
                    sentence.getPublicId());
                return gameSessionRepository
                    .findByMemberAndSentence(member, sentence)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
              }
            });
  }

  private Duration remainingTtl() {
    ZonedDateTime now = ZonedDateTime.now(clock);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(KST);
    Duration remaining = Duration.between(now, midnight);
    return remaining.isNegative() || remaining.isZero() ? Duration.ofMinutes(1) : remaining;
  }

  private DailySentence findTodaySentence() {
    LocalDate today = LocalDate.now(clock);
    return dailySentenceRepository
        .findByUsedAt(today)
        .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
  }

  private DailySentence findTodaySentenceByPublicId(UUID publicId) {
    DailySentence sentence =
        dailySentenceRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
    LocalDate today = LocalDate.now(clock);
    if (!today.equals(sentence.getUsedAt())) {
      throw new BusinessException(ErrorCode.SENTENCE_NOT_FOUND);
    }
    return sentence;
  }

  private Member findMember(UUID publicId) {
    return memberRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  private void validateGuessAllowed(GameSession session) {
    if (session.isInProgress() || session.isCleared()) {
      return;
    }
    throw switch (session.getStatus()) {
      case EXPIRED -> new BusinessException(ErrorCode.GAME_EXPIRED);
      default -> new IllegalStateException("도달할 수 없는 상태: " + session.getStatus());
    };
  }
}
