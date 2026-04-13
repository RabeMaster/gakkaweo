package com.gakkaweo.backend.game.scheduler;

import static com.gakkaweo.backend.common.time.TimeConstants.KST;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import com.gakkaweo.backend.domain.game.entity.GameSession;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import com.gakkaweo.backend.ranking.dto.RankingSnapshot;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
@RequiredArgsConstructor
public class DailySentenceScheduler {

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;
  private final RankingService rankingService;
  private final TransactionTemplate transactionTemplate;
  private final ApplicationEventPublisher eventPublisher;

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void executeMidnightJob() {
    log.info("일일 스케줄러 시작");
    LocalDate today = LocalDate.now(KST);
    LocalDate yesterday = today.minusDays(1);

    try {
      transactionTemplate.executeWithoutResult(status -> expireYesterdaySessions(yesterday));
    } catch (Exception e) {
      log.error("전날 세션 만료 처리 실패: {}", e.getMessage(), e);
    }

    boolean snapshotSuccess = false;
    try {
      RankingSnapshot snapshot = rankingService.getAllRankingsForDate(yesterday);
      transactionTemplate.executeWithoutResult(status -> saveRankingSnapshot(yesterday, snapshot));
      snapshotSuccess = true;
    } catch (Exception e) {
      log.error("전날 랭킹 스냅샷 저장 실패: {}", e.getMessage(), e);
    }

    if (snapshotSuccess) {
      try {
        rankingService.expirePreviousDayRankingKeys(yesterday);
      } catch (Exception e) {
        log.error("전날 랭킹 키 만료 처리 실패: {}", e.getMessage(), e);
      }
    }

    try {
      transactionTemplate.executeWithoutResult(status -> selectTodaySentence(today));
    } catch (Exception e) {
      log.error("오늘의 문장 선정 실패: {}", e.getMessage(), e);
    }

    dailySentenceRepository
        .findByUsedAt(today)
        .ifPresent(s -> eventPublisher.publishEvent(new DayChangeEvent(s.getPublicId())));

    log.info("일일 스케줄러 완료");
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    LocalDate today = LocalDate.now(KST);
    if (dailySentenceRepository.findByUsedAt(today).isEmpty()) {
      log.info("오늘 날짜 기준으로 선택된 문장이 없어 즉시 선정을 진행합니다.");
      executeMidnightJob();
    }
  }

  private void expireYesterdaySessions(LocalDate yesterday) {
    dailySentenceRepository
        .findByUsedAt(yesterday)
        .ifPresent(
            sentence -> {
              int count = gameSessionRepository.expireInProgressSessions(sentence);
              if (count > 0) {
                log.info("전날 세션 만료 처리: date={}, count={}", yesterday, count);
              }
            });
  }

  private void saveRankingSnapshot(LocalDate yesterday, RankingSnapshot snapshot) {
    if (snapshot.memberRanks().isEmpty()) {
      log.info("전날 랭킹 스냅샷: 데이터 없음, date={}", yesterday);
      return;
    }

    DailySentence sentence = dailySentenceRepository.findByUsedAt(yesterday).orElse(null);
    if (sentence == null) {
      log.warn("전날 문장 미발견, 랭킹 스냅샷 건너뜀: date={}", yesterday);
      return;
    }

    sentence.recordTotalPlayers(snapshot.totalPlayers());
    dailySentenceRepository.save(sentence);

    List<GameSession> sessions = gameSessionRepository.findAllBySentenceWithMember(sentence);
    Map<UUID, GameSession> sessionByPublicId =
        sessions.stream()
            .collect(Collectors.toMap(s -> s.getMember().getPublicId(), Function.identity()));

    List<GameSession> updatedSessions = new ArrayList<>();
    for (RankingSnapshot.MemberRank mr : snapshot.memberRanks()) {
      GameSession session = sessionByPublicId.get(mr.publicId());
      if (session != null) {
        session.recordFinalRank(mr.rank());
        updatedSessions.add(session);
      }
    }

    gameSessionRepository.saveAll(updatedSessions);
    log.info(
        "전날 랭킹 스냅샷 저장: date={}, updated={}, totalPlayers={}",
        yesterday,
        updatedSessions.size(),
        snapshot.totalPlayers());
  }

  private void selectTodaySentence(LocalDate today) {
    if (dailySentenceRepository.findByUsedAt(today).isPresent()) {
      log.info("오늘의 문장이 이미 선정됨: date={}", today);
      return;
    }

    DailySentence sentence =
        dailySentenceRepository
            .findByScheduledAt(today)
            .or(dailySentenceRepository::findRandomUnusedSentence)
            .orElseThrow(() -> new IllegalStateException("사용 가능한 문장이 없음 — 문장 추가 필요"));

    boolean wasScheduled = sentence.getScheduledAt() != null;
    sentence.setUsedAt(today);
    sentence.setStatus(DailySentenceStatus.USED);
    sentence.setScheduledAt(null);
    dailySentenceRepository.save(sentence);
    log.info(
        "오늘의 문장 선정: date={}, sentenceId={}, scheduled={}",
        today,
        sentence.getPublicId(),
        wasScheduled);
  }
}
