package com.gakkaweo.backend.ranking.sse;

import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.event.DayChangeEvent;
import com.gakkaweo.backend.ranking.event.RankingUpdateEvent;
import com.gakkaweo.backend.ranking.service.RankingService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SseEventListener {

  private static final long DEBOUNCE_MILLIS = 100;

  private final RankingService rankingService;
  private final SseConnectionManager sseConnectionManager;
  private final ScheduledExecutorService debounceExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sse-debounce");
            t.setDaemon(true);
            return t;
          });
  private volatile ScheduledFuture<?> pendingBroadcast;

  public SseEventListener(
      RankingService rankingService, SseConnectionManager sseConnectionManager) {
    this.rankingService = rankingService;
    this.sseConnectionManager = sseConnectionManager;
  }

  @PreDestroy
  void shutdown() {
    debounceExecutor.shutdownNow();
  }

  @EventListener
  public synchronized void onRankingUpdate(RankingUpdateEvent event) {
    if (pendingBroadcast != null) {
      pendingBroadcast.cancel(false);
    }
    pendingBroadcast =
        debounceExecutor.schedule(this::broadcastRanking, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
  }

  @EventListener
  public void onDayChange(DayChangeEvent event) {
    sseConnectionManager.broadcast(
        SseEventType.DAY_CHANGE, Map.of("newSentenceId", event.newSentenceId()));
    log.info("DAY_CHANGE 이벤트 브로드캐스트: newSentenceId={}", event.newSentenceId());
  }

  private void broadcastRanking() {
    try {
      RankingResponse ranking = rankingService.getRankings();
      sseConnectionManager.broadcast(SseEventType.RANKING_UPDATE, ranking);
      log.debug("RANKING_UPDATE 브로드캐스트: totalPlayers={}", ranking.totalPlayers());
    } catch (Exception e) {
      log.error("RANKING_UPDATE 브로드캐스트 실패: {}", e.getMessage());
    }
  }
}
