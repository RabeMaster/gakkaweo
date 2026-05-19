package com.gakkaweo.backend.multiplayer.room;

import com.gakkaweo.backend.multiplayer.room.dto.RoomCreateRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomDelegateRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomJoinRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomKickRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomSettingsRequest;
import com.gakkaweo.backend.multiplayer.room.dto.RoomSnapshot;
import com.gakkaweo.backend.multiplayer.room.dto.WsNotification;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

  private static final long COUNTDOWN_SECONDS = 3;

  private final RoomManager roomManager;
  private final SimpMessagingTemplate messagingTemplate;
  private final ApplicationEventPublisher eventPublisher;
  private final ScheduledExecutorService multiplayerTimerExecutor;
  private final Clock clock;

  public RoomSnapshot createRoom(RoomCreateRequest request, UUID publicId, String nickname) {
    RoomSettings settings =
        new RoomSettings(
            request.title(),
            request.mode(),
            request.rounds(),
            request.timeLimit(),
            request.maxPlayers(),
            request.password(),
            request.guessPublic());

    Room room = roomManager.createRoom(settings, publicId, nickname, clock);
    RoomSnapshot snapshot = snapshotUnderLock(room);

    eventPublisher.publishEvent(new RoomStateChangedEvent(room.getId(), RoomChangeType.CREATED));
    return snapshot;
  }

  public RoomSnapshot joinRoom(
      String roomId, RoomJoinRequest request, UUID publicId, String nickname) {
    Room room = getOrThrow(roomId);

    if (!roomManager.registerMembership(publicId, roomId)) {
      throw RoomException.joinFailed(RoomException.Reason.ALREADY_IN_ROOM, "이미 다른 방에 참가 중입니다");
    }

    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      if (!room.checkPassword(request.password())) {
        roomManager.unregisterMembership(publicId);
        throw RoomException.joinFailed(RoomException.Reason.WRONG_PASSWORD, "비밀번호가 올바르지 않습니다");
      }
      room.addPlayer(publicId, nickname, clock.instant());
      snapshot = RoomSnapshot.from(room);
    } catch (RoomException e) {
      roomManager.unregisterMembership(publicId);
      throw e;
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("PLAYER_JOINED", snapshot));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.PLAYER_JOINED));
    return snapshot;
  }

  public void leaveRoom(String roomId, UUID publicId) {
    Room room = getOrThrow(roomId);

    Room.RemoveResult result;
    RoomSnapshot snapshot = null;
    boolean revertedToWaiting = false;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      if (room.getStatus() == RoomStatus.COUNTDOWN && room.getPlayerCount() <= 2) {
        room.revertToWaiting();
        revertedToWaiting = true;
      }
      result = room.removePlayer(publicId);
      if (result.removed() && !result.isEmpty()) {
        snapshot = RoomSnapshot.from(room);
      }
    } finally {
      lock.unlock();
    }

    if (!result.removed()) {
      return;
    }
    roomManager.unregisterMembership(publicId);

    if (result.isEmpty()) {
      roomManager.removeRoom(roomId);
      eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.DESTROYED));
      return;
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("PLAYER_LEFT", snapshot));
    if (revertedToWaiting) {
      messagingTemplate.convertAndSend(
          "/topic/room/" + roomId, new WsNotification("COUNTDOWN_CANCELLED", snapshot));
    }
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.PLAYER_LEFT));
  }

  public boolean toggleReady(String roomId, UUID publicId) {
    Room room = getOrThrow(roomId);

    boolean ready;
    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      ready = room.toggleReady(publicId);
      snapshot = RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("PLAYER_READY", snapshot));
    return ready;
  }

  public void startGame(String roomId, UUID publicId) {
    Room room = getOrThrow(roomId);

    Instant endsAt;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      room.validateAndPrepareStart(publicId);
      room.transitionToCountdown();
      endsAt = clock.instant().plusSeconds(COUNTDOWN_SECONDS);

      ScheduledFuture<?> future =
          multiplayerTimerExecutor.schedule(
              () -> transitionToPlaying(roomId), COUNTDOWN_SECONDS, TimeUnit.SECONDS);
      room.setCountdownFuture(future);
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId + "/game",
        new WsNotification(
            "COUNTDOWN", Map.of("endsAt", endsAt.toEpochMilli(), "serverNow", clock.millis())));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.STATUS_CHANGED));
  }

  public void updateSettings(String roomId, RoomSettingsRequest request, UUID publicId) {
    Room room = getOrThrow(roomId);

    RoomSettings newSettings =
        new RoomSettings(
            request.title(),
            request.mode(),
            request.rounds(),
            request.timeLimit(),
            request.maxPlayers(),
            request.password(),
            request.guessPublic());

    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      room.updateSettings(publicId, newSettings);
      snapshot = RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("ROOM_SETTINGS_UPDATED", snapshot));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.SETTINGS_UPDATED));
  }

  public void kickPlayer(String roomId, RoomKickRequest request, UUID publicId) {
    Room room = getOrThrow(roomId);

    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      room.kick(publicId, request.targetPublicId());
      snapshot = RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }

    roomManager.unregisterMembership(request.targetPublicId());

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("PLAYER_KICKED", snapshot));
    messagingTemplate.convertAndSendToUser(
        request.targetPublicId().toString(),
        "/queue/notifications",
        new WsNotification("KICKED", Map.of("roomId", roomId)));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.PLAYER_KICKED));
  }

  public void delegateHost(String roomId, RoomDelegateRequest request, UUID publicId) {
    Room room = getOrThrow(roomId);

    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      room.delegateHost(publicId, request.targetPublicId());
      snapshot = RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId, new WsNotification("HOST_DELEGATED", snapshot));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.HOST_DELEGATED));
  }

  public RoomSnapshot quickJoin(UUID publicId, String nickname) {
    Room candidate = roomManager.findQuickJoinRoom();
    if (candidate == null) {
      throw RoomException.quickJoinFailed(RoomException.Reason.NO_ROOM_AVAILABLE, "입장 가능한 방이 없습니다");
    }
    return joinRoom(candidate.getId(), new RoomJoinRequest(null), publicId, nickname);
  }

  public void handleDisconnect(SessionDisconnectEvent event) {
    if (event.getUser() == null) {
      return;
    }
    try {
      UUID publicId = UUID.fromString(event.getUser().getName());
      String roomId = roomManager.getActiveRoomId(publicId);
      if (roomId != null) {
        leaveRoom(roomId, publicId);
      }
    } catch (IllegalArgumentException e) {
      log.warn("WS disconnect - invalid principal: {}", event.getUser().getName());
    }
  }

  private void transitionToPlaying(String roomId) {
    Room room = roomManager.getRoom(roomId);
    if (room == null) {
      return;
    }

    RoomSnapshot snapshot;
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      if (room.getStatus() != RoomStatus.COUNTDOWN) {
        return;
      }
      room.transitionToPlaying();
      snapshot = RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }

    messagingTemplate.convertAndSend(
        "/topic/room/" + roomId + "/game", new WsNotification("GAME_STARTED", snapshot));
    eventPublisher.publishEvent(new RoomStateChangedEvent(roomId, RoomChangeType.STATUS_CHANGED));
  }

  private RoomSnapshot snapshotUnderLock(Room room) {
    ReentrantLock lock = room.getLock();
    lock.lock();
    try {
      return RoomSnapshot.from(room);
    } finally {
      lock.unlock();
    }
  }

  private Room getOrThrow(String roomId) {
    Room room = roomManager.getRoom(roomId);
    if (room == null) {
      throw RoomException.joinFailed(RoomException.Reason.ROOM_NOT_FOUND, "방을 찾을 수 없습니다");
    }
    return room;
  }
}
