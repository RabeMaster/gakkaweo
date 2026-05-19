package com.gakkaweo.backend.multiplayer.room;

import com.gakkaweo.backend.multiplayer.room.dto.LobbyRoomInfo;
import com.gakkaweo.backend.multiplayer.room.dto.WsNotification;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LobbyReadModel {

  private static final long THROTTLE_MS = 500;

  private final RoomManager roomManager;
  private final SimpMessagingTemplate messagingTemplate;

  @Qualifier("multiplayerTimerExecutor")
  private final ScheduledExecutorService executor;

  private final ConcurrentHashMap<String, LobbyRoomInfo> rooms = new ConcurrentHashMap<>();
  private final AtomicBoolean dirty = new AtomicBoolean(false);
  private volatile ScheduledFuture<?> pendingBroadcast;

  @EventListener
  public void onRoomStateChanged(RoomStateChangedEvent event) {
    String roomId = event.roomId();

    if (event.type() == RoomChangeType.DESTROYED) {
      rooms.remove(roomId);
    } else {
      Room room = roomManager.getRoom(roomId);
      if (room != null) {
        rooms.put(roomId, LobbyRoomInfo.from(room));
      } else {
        rooms.remove(roomId);
      }
    }

    scheduleThrottledBroadcast();
  }

  public List<LobbyRoomInfo> getSnapshot() {
    return List.copyOf(rooms.values());
  }

  private void scheduleThrottledBroadcast() {
    if (dirty.compareAndSet(false, true)) {
      pendingBroadcast =
          executor.schedule(this::broadcastLobby, THROTTLE_MS, TimeUnit.MILLISECONDS);
    }
  }

  private void broadcastLobby() {
    dirty.set(false);
    List<LobbyRoomInfo> snapshot = getSnapshot();
    messagingTemplate.convertAndSend("/topic/lobby", new WsNotification("LOBBY_UPDATE", snapshot));
  }
}
