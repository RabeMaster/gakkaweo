package com.gakkaweo.backend.ranking.service;

import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RankingFinalizer {

  private final RankingWriteCoordinator rankingWriteCoordinator;
  private final TransactionTemplate transactionTemplate;
  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final RankingService rankingService;

  public RankingSnapshot finalizeForDate(LocalDate date) {
    return rankingWriteCoordinator.write(
        transactionTemplate,
        status -> {
          var sentence = dailySentenceRepository.findByUsedAt(date).orElse(null);
          if (sentence == null) {
            return new RankingSnapshot(List.of(), 0);
          }
          gameSessionRepository.expireInProgressSessions(sentence);
          sentence = dailySentenceRepository.findByUsedAt(date).orElseThrow();
          var sessions = gameSessionRepository.findAllBySentenceWithMember(sentence);
          boolean incomplete =
              sessions.stream()
                  .anyMatch(
                      session ->
                          session.getMember() != null
                              && session.getBestSimilarity().signum() > 0
                              && session.getFinalRank() == null);
          if (sentence.getTotalPlayers() != null && !incomplete) {
            return new RankingSnapshot(List.of(), sentence.getTotalPlayers());
          }

          // 정본 조회와 확정은 하나의 조정 잠금과 DB 트랜잭션 안에서 수행합니다.
          RankingSnapshot snapshot = rankingService.getCanonicalSnapshotForDate(date);
          Map<UUID, GameSession> byMember =
              sessions.stream()
                  .filter(session -> session.getMember() != null)
                  .collect(
                      Collectors.toMap(
                          session -> session.getMember().getPublicId(), Function.identity()));
          sentence.recordTotalPlayers(
              Math.max(
                  snapshot.totalPlayers(),
                  sentence.getTotalPlayers() == null ? 0 : sentence.getTotalPlayers()));
          snapshot
              .memberRanks()
              .forEach(rank -> byMember.get(rank.publicId()).recordFinalRank(rank.rank()));
          return new RankingSnapshot(snapshot.memberRanks(), sentence.getTotalPlayers());
        });
  }
}
