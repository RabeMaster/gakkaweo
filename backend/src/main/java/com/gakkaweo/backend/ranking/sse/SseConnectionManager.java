package com.gakkaweo.backend.ranking.sse;

import com.gakkaweo.backend.common.exception.BusinessException;
import com.gakkaweo.backend.common.exception.ErrorCode;
import com.gakkaweo.backend.ranking.dto.HeartbeatResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
@RequiredArgsConstructor
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

  public synchronized SseEmitter register() {
    if (emitters.size() >= MAX_CONNECTIONS) {
      throw new BusinessException(ErrorCode.SSE_MAX_CONNECTIONS);
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

  public int getConnectionCount() {
    return emitters.size();
  }

  public void broadcast(SseEventType type, Object data) {
    sendToAll(emitter -> emitter.send(SseEmitter.event().name(type.name()).data(data)));
  }

  private SseEmitter.SseEventBuilder heartbeatEvent() {
    return SseEmitter.event()
        .name(SseEventType.HEARTBEAT.name())
        .data(new HeartbeatResponse(getConnectionCount()));
  }

  private void sendHeartbeat() {
    try {
      if (emitters.isEmpty()) {
        return;
      }
      SseEmitter.SseEventBuilder event = heartbeatEvent();
      sendToAll(emitter -> emitter.send(event));
    } catch (Exception e) {
      log.error("SSE heartbeat 처리 중 예외", e);
    }
  }

  private void sendInitialRanking(SseEmitter emitter) {
    try {
      emitter.send(heartbeatEvent());
      RankingResponse ranking = rankingService.getRankings();
      emitter.send(SseEmitter.event().name(SseEventType.RANKING_UPDATE.name()).data(ranking));
    } catch (Exception e) {
      removeAndComplete(emitter);
      log.debug("SSE 초기 데이터 전송 실패: {}", e.getMessage(), e);
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
