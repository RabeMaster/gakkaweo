package com.gakkaweo.backend.game.service;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.member.entity.Member;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.game.config.GameProperties;
import com.gakkaweo.backend.game.dto.GameStatusResponse;
import com.gakkaweo.backend.game.dto.GiveUpResponse;
import com.gakkaweo.backend.game.dto.GuessHistoryResponse;
import com.gakkaweo.backend.game.dto.GuessRequest;
import com.gakkaweo.backend.game.dto.GuessResponse;
import com.gakkaweo.backend.game.dto.TodayResponse;
import com.gakkaweo.backend.game.util.HintMaskGenerator;
import com.gakkaweo.backend.infra.ai.service.SimilarityService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyGameService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final GuessHistoryRepository guessHistoryRepository;
  private final MemberRepository memberRepository;
  private final SimilarityService similarityService;
  private final HintMaskGenerator hintMaskGenerator;
  private final GameProperties gameProperties;

  public DailyGameService(
      DailySentenceRepository dailySentenceRepository,
      GameSessionRepository gameSessionRepository,
      GuessHistoryRepository guessHistoryRepository,
      MemberRepository memberRepository,
      SimilarityService similarityService,
      HintMaskGenerator hintMaskGenerator,
      GameProperties gameProperties) {
    this.dailySentenceRepository = dailySentenceRepository;
    this.gameSessionRepository = gameSessionRepository;
    this.guessHistoryRepository = guessHistoryRepository;
    this.memberRepository = memberRepository;
    this.similarityService = similarityService;
    this.hintMaskGenerator = hintMaskGenerator;
    this.gameProperties = gameProperties;
  }

  @Transactional(readOnly = true)
  public TodayResponse getToday() {
    DailySentence sentence = findTodaySentence();
    HintMaskGenerator.HintMask hint = hintMaskGenerator.generate(sentence.getSentence());

    LocalDateTime expiresAt = LocalDate.now(KST).plusDays(1).atStartOfDay();

    return new TodayResponse(
        sentence.getPublicId(),
        hint.mask(),
        hint.charCounts().size(),
        hint.charCounts(),
        expiresAt);
  }

  @Transactional
  public GuessResponse guessAuthenticated(GuessRequest request, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(request.sentenceId());
    Member member = findMember(memberPublicId);

    GameSession session = findOrCreateSession(member, sentence);

    validateInProgress(session);

    BigDecimal similarity =
        similarityService.calculateSimilarity(
            sentence.getId(), request.guessText(), sentence.getSentence(), remainingTtl());

    boolean isCorrect = similarity.compareTo(gameProperties.getSimilarityThreshold()) >= 0;

    session.incrementAttempt();
    session.updateBestSimilarity(similarity);
    if (isCorrect) {
      session.markCleared();
    }

    guessHistoryRepository.save(
        new GuessHistory(session, request.guessText(), similarity, session.getAttemptCount()));

    gameSessionRepository.save(session);

    return new GuessResponse(
        similarity,
        session.getAttemptCount(),
        isCorrect,
        session.getStatus().name(),
        LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public GuessResponse guessAnonymous(GuessRequest request) {
    DailySentence sentence = findTodaySentenceByPublicId(request.sentenceId());

    BigDecimal similarity =
        similarityService.calculateSimilarity(
            sentence.getId(), request.guessText(), sentence.getSentence(), remainingTtl());

    boolean isCorrect = similarity.compareTo(gameProperties.getSimilarityThreshold()) >= 0;

    return new GuessResponse(similarity, null, isCorrect, null, LocalDateTime.now());
  }

  @Transactional
  public GiveUpResponse giveUp(UUID sentenceId, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(sentenceId);
    Member member = findMember(memberPublicId);

    GameSession session =
        gameSessionRepository
            .findByMemberAndSentence(member, sentence)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

    validateInProgress(session);

    session.markGivenUp();
    gameSessionRepository.save(session);

    return new GiveUpResponse(
        sentence.getSentence(),
        session.getAttemptCount(),
        session.getBestSimilarity(),
        session.getStatus().name());
  }

  @Transactional(readOnly = true)
  public GuessHistoryResponse getHistory(UUID sentenceId, UUID memberPublicId) {
    DailySentence sentence = findTodaySentenceByPublicId(sentenceId);
    Member member = findMember(memberPublicId);

    GameSession session =
        gameSessionRepository
            .findByMemberAndSentence(member, sentence)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

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
                return gameSessionRepository
                    .findByMemberAndSentence(member, sentence)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
              }
            });
  }

  private Duration remainingTtl() {
    ZonedDateTime now = ZonedDateTime.now(KST);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(KST);
    Duration remaining = Duration.between(now, midnight);
    return remaining.isNegative() || remaining.isZero() ? Duration.ofMinutes(1) : remaining;
  }

  private DailySentence findTodaySentence() {
    LocalDate today = LocalDate.now(KST);
    return dailySentenceRepository
        .findByUsedAt(today)
        .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
  }

  private DailySentence findTodaySentenceByPublicId(UUID publicId) {
    DailySentence sentence =
        dailySentenceRepository
            .findByPublicId(publicId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));
    LocalDate today = LocalDate.now(KST);
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

  private void validateInProgress(GameSession session) {
    if (!session.isInProgress()) {
      throw switch (session.getStatus()) {
        case CLEARED -> new BusinessException(ErrorCode.GAME_ALREADY_CLEARED);
        case GIVEN_UP -> new BusinessException(ErrorCode.GAME_ALREADY_GIVEN_UP);
        case EXPIRED ->
            new BusinessException(
                ErrorCode.GAME_EXPIRED); // 자정 스케줄러에서 IN_PROGRESS → EXPIRED 일괄 전이 예정
        default -> new IllegalStateException("도달할 수 없는 상태: " + session.getStatus());
      };
    }
  }
}
