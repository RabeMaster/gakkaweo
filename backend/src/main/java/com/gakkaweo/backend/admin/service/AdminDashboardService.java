package com.gakkaweo.backend.admin.service;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.admin.dto.DateStatsResponse;
import com.gakkaweo.backend.admin.dto.FullRankingResponse;
import com.gakkaweo.backend.admin.dto.FullRankingResponse.RankingEntry;
import com.gakkaweo.backend.admin.dto.GuessLogResponse;
import com.gakkaweo.backend.admin.dto.GuessLogResponse.GuessLogEntry;
import com.gakkaweo.backend.admin.dto.TodayWidgetResponse;
import com.gakkaweo.backend.admin.dto.TrendDataResponse;
import com.gakkaweo.backend.admin.dto.TrendDataResponse.DailyTrend;
import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.GuessHistory;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.domain.game.repository.GuessHistoryRepository;
import com.gakkaweo.backend.domain.member.repository.MemberRepository;
import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import com.gakkaweo.backend.ranking.sse.SseConnectionManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final GuessHistoryRepository guessHistoryRepository;
  private final MemberRepository memberRepository;
  private final RankingService rankingService;
  private final SseConnectionManager sseConnectionManager;
  private final Clock clock;

  @Transactional(readOnly = true)
  public TodayWidgetResponse getTodayWidget() {
    LocalDate today = LocalDate.now(clock);
    DailySentence sentence =
        dailySentenceRepository
            .findByUsedAt(today)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));

    SentenceStatsSnapshot stats = collectSentenceStats(sentence);
    long unusedCount = dailySentenceRepository.countUnusedActive();
    int sseCount = sseConnectionManager.getConnectionCount();

    return new TodayWidgetResponse(
        sentence.getPublicId(),
        sentence.getSentence(),
        stats.totalParticipants(),
        stats.clearedCount(),
        stats.totalParticipants() - stats.clearedCount(),
        stats.avgSimilarity(),
        stats.avgAttemptCount(),
        unusedCount,
        sseCount);
  }

  public FullRankingResponse getFullRanking(LocalDate date) {
    if (date == null) {
      date = LocalDate.now(clock);
    }
    RankingResponse ranking = rankingService.getFullRankingsForDate(date);

    List<RankingEntry> entries = ranking.rankings().stream().map(RankingEntry::from).toList();

    return new FullRankingResponse(entries, ranking.totalPlayers());
  }

  @Transactional(readOnly = true)
  public DateStatsResponse getDateStats(LocalDate date) {
    DailySentence sentence =
        dailySentenceRepository
            .findByUsedAt(date)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));

    SentenceStatsSnapshot stats = collectSentenceStats(sentence);

    return DateStatsResponse.from(
        date,
        sentence,
        stats.totalParticipants(),
        stats.clearedCount(),
        stats.avgSimilarity(),
        stats.avgAttemptCount());
  }

  @Transactional(readOnly = true)
  public TrendDataResponse getTrends(int days) {
    LocalDate today = LocalDate.now(clock);
    List<DailyTrend> trends = new ArrayList<>();

    for (int i = days - 1; i >= 0; i--) {
      LocalDate date = today.minusDays(i);
      Instant dayStart = date.atStartOfDay(KST).toInstant();
      Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();

      long participants = 0;
      long clears = 0;
      double clearRate = 0;

      var sentenceOpt = dailySentenceRepository.findByUsedAt(date);
      if (sentenceOpt.isPresent()) {
        DailySentence sentence = sentenceOpt.get();
        participants = gameSessionRepository.countBySentence(sentence);
        clears = gameSessionRepository.countClearedBySentence(sentence);
        clearRate = participants > 0 ? (double) clears / participants * 100 : 0;
      }

      long newMembers = memberRepository.countByCreatedAtBetween(dayStart, dayEnd);

      trends.add(new DailyTrend(date, participants, clears, clearRate, newMembers));
    }

    return new TrendDataResponse(trends);
  }

  @Transactional(readOnly = true)
  public GuessLogResponse getGuessLog(LocalDate date, UUID memberPublicId) {
    DailySentence sentence =
        dailySentenceRepository
            .findByUsedAt(date)
            .orElseThrow(() -> new BusinessException(ErrorCode.SENTENCE_NOT_FOUND));

    List<GuessHistory> guesses;
    if (memberPublicId != null) {
      guesses = guessHistoryRepository.findBySentenceAndMember(sentence, memberPublicId);
    } else {
      guesses =
          guessHistoryRepository
              .findBySentenceOrderByCreatedAtDesc(sentence, PageRequest.of(0, 500))
              .getContent();
    }

    List<GuessLogEntry> logs = guesses.stream().map(GuessLogEntry::from).toList();

    return new GuessLogResponse(logs);
  }

  private SentenceStatsSnapshot collectSentenceStats(DailySentence sentence) {
    long totalParticipants = gameSessionRepository.countBySentence(sentence);
    long clearedCount = gameSessionRepository.countClearedBySentence(sentence);
    BigDecimal avgSimilarity = gameSessionRepository.avgSimilarityBySentence(sentence);
    double avgAttemptCount = gameSessionRepository.avgAttemptCountBySentence(sentence);
    return new SentenceStatsSnapshot(
        totalParticipants, clearedCount, avgSimilarity, avgAttemptCount);
  }

  private record SentenceStatsSnapshot(
      long totalParticipants,
      long clearedCount,
      BigDecimal avgSimilarity,
      double avgAttemptCount) {}
}
