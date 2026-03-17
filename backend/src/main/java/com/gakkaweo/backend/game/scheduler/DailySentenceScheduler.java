package com.gakkaweo.backend.game.scheduler;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.repository.DailySentenceRepository;
import com.gakkaweo.backend.domain.game.repository.GameSessionRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DailySentenceScheduler {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final DailySentenceRepository dailySentenceRepository;
  private final GameSessionRepository gameSessionRepository;

  public DailySentenceScheduler(
      DailySentenceRepository dailySentenceRepository,
      GameSessionRepository gameSessionRepository) {
    this.dailySentenceRepository = dailySentenceRepository;
    this.gameSessionRepository = gameSessionRepository;
  }

  @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
  public void selectDailySentence() {
    log.info("일일 스케줄러 시작");
    try {
      expireYesterdaySessions();
      selectTodaySentence();
      log.info("일일 스케줄러 완료");
    } catch (Exception e) {
      log.error("일일 스케줄러 실패: {}", e.getMessage(), e);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    LocalDate today = LocalDate.now(KST);
    if (dailySentenceRepository.findByUsedAt(today).isEmpty()) {
      log.info("오늘 날짜 기준으로 선택된 문장이 없어 즉시 선정을 진행합니다.");
      selectDailySentence();
    }
  }

  @Transactional
  public void expireYesterdaySessions() {
    LocalDate yesterday = LocalDate.now(KST).minusDays(1);
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

  @Transactional
  public void selectTodaySentence() {
    LocalDate today = LocalDate.now(KST);

    if (dailySentenceRepository.findByUsedAt(today).isPresent()) {
      log.info("오늘의 문장이 이미 선정됨: date={}", today);
      return;
    }

    DailySentence sentence =
        dailySentenceRepository
            .findRandomUnusedSentence()
            .orElseThrow(
                () -> {
                  log.error("사용 가능한 문장이 없음 — 문장 추가 필요");
                  return new IllegalStateException("사용 가능한 문장이 없습니다");
                });

    sentence.setUsedAt(today);
    dailySentenceRepository.save(sentence);
    log.info("오늘의 문장 선정: date={}, sentenceId={}", today, sentence.getPublicId());
  }
}
