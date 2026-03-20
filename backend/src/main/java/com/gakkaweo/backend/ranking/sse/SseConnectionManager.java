package com.gakkaweo.backend.ranking.sse;

import com.gakkaweo.backend.ranking.dto.RankingResponse;
import com.gakkaweo.backend.ranking.service.RankingService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class SseConnectionManager {

  private static final int MAX_CONNECTIONS = 500;
  private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

  private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
  private final RankingService rankingService;
  private final ScheduledExecutorService heartbeatExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
          });

  public SseConnectionManager(RankingService rankingService) {
    this.rankingService = rankingService;
  }

  @PostConstruct
  void startHeartbeat() {
    heartbeatExecutor.scheduleAtFixedRate(
        this::sendHeartbeat,
        HEARTBEAT_INTERVAL_SECONDS,
        HEARTBEAT_INTERVAL_SECONDS,
        TimeUnit.SECONDS);
  }

  @PreDestroy
  void shutdown() {
    heartbeatExecutor.shutdownNow();
    emitters.forEach(this::completeQuietly);
    emitters.clear();
  }

  public SseEmitter register() {
    if (emitters.size() >= MAX_CONNECTIONS) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SSE 최대 연결 수 초과");
    }

    SseEmitter emitter = new SseEmitter(0L);

    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> removeAndComplete(emitter));
    emitter.onError(e -> removeAndComplete(emitter));

    emitters.add(emitter);
    sendInitialRanking(emitter);

    log.debug("SSE 연결 등록: 현재 연결 수={}", emitters.size());
    return emitter;
  }

  public void broadcast(SseEventType type, Object data) {
    sendToAll(emitter -> emitter.send(SseEmitter.event().name(type.name()).data(data)));
  }

  private void sendHeartbeat() {
    try {
      if (emitters.isEmpty()) {
        return;
      }
      sendToAll(
          emitter -> emitter.send(SseEmitter.event().name(SseEventType.HEARTBEAT.name()).data("")));
    } catch (Exception e) {
      log.error("SSE heartbeat 처리 중 예외: {}", e.getMessage());
    }
  }

  private void sendInitialRanking(SseEmitter emitter) {
    try {
      RankingResponse ranking = rankingService.getRankings();
      emitter.send(SseEmitter.event().name(SseEventType.RANKING_UPDATE.name()).data(ranking));
    } catch (Exception e) {
      removeAndComplete(emitter);
      log.debug("SSE 초기 데이터 전송 실패: {}", e.getMessage());
    }
  }

  private void sendToAll(EmitterAction action) {
    List<SseEmitter> dead = new ArrayList<>();
    for (SseEmitter emitter : emitters) {
      try {
        action.execute(emitter);
      } catch (Exception e) {
        dead.add(emitter);
        completeQuietly(emitter);
      }
    }
    if (!dead.isEmpty()) {
      emitters.removeAll(dead);
      log.debug("SSE 실패 연결 제거: removed={}, remaining={}", dead.size(), emitters.size());
    }
  }

  private void removeAndComplete(SseEmitter emitter) {
    emitters.remove(emitter);
    completeQuietly(emitter);
  }

  private void completeQuietly(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception ignored) {
    }
  }

  @FunctionalInterface
  private interface EmitterAction {
    void execute(SseEmitter emitter) throws Exception;
  }
}
