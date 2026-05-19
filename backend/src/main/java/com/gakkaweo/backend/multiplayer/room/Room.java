package com.gakkaweo.backend.multiplayer.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

public class Room {

  private final String id;
  private final Instant createdAt;
  private final ReentrantLock lock = new ReentrantLock();
  private final LinkedHashMap<UUID, RoomPlayer> players = new LinkedHashMap<>();

  private RoomSettings settings;
  private RoomStatus status;
  private UUID hostPublicId;
  private ScheduledFuture<?> countdownFuture;

  public Room(String id, RoomSettings settings, UUID hostPublicId, Instant createdAt) {
    this.id = id;
    this.settings = settings;
    this.hostPublicId = hostPublicId;
    this.status = RoomStatus.WAITING;
    this.createdAt = createdAt;
  }

  public void addPlayer(UUID publicId, String nickname, Instant joinedAt) {
    if (status != RoomStatus.WAITING) {
      throw RoomException.joinFailed(RoomException.Reason.ROOM_NOT_WAITING, "대기 중인 방에만 입장할 수 있습니다");
    }
    if (players.size() >= settings.maxPlayers()) {
      throw RoomException.joinFailed(RoomException.Reason.ROOM_FULL, "방이 가득 찼습니다");
    }
    RoomPlayer player = new RoomPlayer(publicId, nickname, joinedAt);
    players.put(publicId, player);
    if (players.size() == 1) {
      hostPublicId = publicId;
    }
  }

  public RemoveResult removePlayer(UUID publicId) {
    RoomPlayer removed = players.remove(publicId);
    if (removed == null) {
      return new RemoveResult(false, null, false);
    }

    if (players.isEmpty()) {
      status = RoomStatus.ABANDONED;
      return new RemoveResult(true, null, true);
    }

    UUID newHost = null;
    if (publicId.equals(hostPublicId)) {
      newHost = players.keySet().iterator().next();
      hostPublicId = newHost;
    }
    return new RemoveResult(true, newHost, false);
  }

  public boolean toggleReady(UUID publicId) {
    if (status != RoomStatus.WAITING) {
      throw RoomException.readyFailed(
          RoomException.Reason.ROOM_NOT_WAITING, "대기 중일 때만 준비 상태를 변경할 수 있습니다");
    }
    RoomPlayer player = players.get(publicId);
    if (player == null) {
      throw RoomException.readyFailed(RoomException.Reason.PLAYER_NOT_FOUND, "방에 참가하고 있지 않습니다");
    }
    player.toggleReady();
    return player.isReady();
  }

  public void validateAndPrepareStart(UUID requestPublicId) {
    if (!requestPublicId.equals(hostPublicId)) {
      throw RoomException.startFailed(RoomException.Reason.NOT_HOST, "방장만 게임을 시작할 수 있습니다");
    }
    if (status != RoomStatus.WAITING) {
      throw RoomException.startFailed(
          RoomException.Reason.ROOM_NOT_WAITING, "대기 중일 때만 게임을 시작할 수 있습니다");
    }
    boolean allReady =
        players.entrySet().stream()
            .filter(e -> !e.getKey().equals(hostPublicId))
            .allMatch(e -> e.getValue().isReady());
    if (!allReady) {
      throw RoomException.startFailed(
          RoomException.Reason.NOT_ALL_READY, "모든 플레이어가 준비해야 게임을 시작할 수 있습니다");
    }
    if (players.size() < 2) {
      throw RoomException.startFailed(RoomException.Reason.NOT_ALL_READY, "최소 2명이 필요합니다");
    }
  }

  public void transitionToCountdown() {
    this.status = RoomStatus.COUNTDOWN;
  }

  public void transitionToPlaying() {
    this.status = RoomStatus.PLAYING;
  }

  public void updateSettings(UUID requestPublicId, RoomSettings newSettings) {
    if (!requestPublicId.equals(hostPublicId)) {
      throw RoomException.settingsFailed(RoomException.Reason.NOT_HOST, "방장만 설정을 변경할 수 있습니다");
    }
    if (status != RoomStatus.WAITING) {
      throw RoomException.settingsFailed(
          RoomException.Reason.ROOM_NOT_WAITING, "대기 중일 때만 설정을 변경할 수 있습니다");
    }
    newSettings.validate();
    this.settings = newSettings;
  }

  public UUID kick(UUID requestPublicId, UUID targetPublicId) {
    if (!requestPublicId.equals(hostPublicId)) {
      throw RoomException.kickFailed(RoomException.Reason.NOT_HOST, "방장만 강퇴할 수 있습니다");
    }
    if (status != RoomStatus.WAITING) {
      throw RoomException.kickFailed(RoomException.Reason.ROOM_NOT_WAITING, "대기 중일 때만 강퇴할 수 있습니다");
    }
    if (requestPublicId.equals(targetPublicId)) {
      throw RoomException.kickFailed(RoomException.Reason.CANNOT_SELF, "자기 자신을 강퇴할 수 없습니다");
    }
    RoomPlayer removed = players.remove(targetPublicId);
    if (removed == null) {
      throw RoomException.kickFailed(RoomException.Reason.PLAYER_NOT_FOUND, "해당 플레이어를 찾을 수 없습니다");
    }
    return targetPublicId;
  }

  public void delegateHost(UUID currentHost, UUID newHost) {
    if (!currentHost.equals(hostPublicId)) {
      throw RoomException.delegateFailed(RoomException.Reason.NOT_HOST, "방장만 위임할 수 있습니다");
    }
    if (!players.containsKey(newHost)) {
      throw RoomException.delegateFailed(
          RoomException.Reason.PLAYER_NOT_FOUND, "해당 플레이어를 찾을 수 없습니다");
    }
    if (currentHost.equals(newHost)) {
      throw RoomException.delegateFailed(RoomException.Reason.CANNOT_SELF, "자기 자신에게 위임할 수 없습니다");
    }
    this.hostPublicId = newHost;
  }

  public boolean checkPassword(String input) {
    if (!settings.hasPassword()) {
      return true;
    }
    return settings.password().equals(input);
  }

  public boolean allowsChat() {
    return status == RoomStatus.WAITING
        || status == RoomStatus.ROUND_RESULT
        || status == RoomStatus.GAME_RESULT;
  }

  public void cancelCountdown() {
    if (countdownFuture != null && !countdownFuture.isDone()) {
      countdownFuture.cancel(false);
      countdownFuture = null;
    }
  }

  public void resetReady() {
    players.values().forEach(p -> p.setReady(false));
  }

  public void revertToWaiting() {
    cancelCountdown();
    this.status = RoomStatus.WAITING;
    resetReady();
  }

  public String getId() {
    return id;
  }

  public RoomSettings getSettings() {
    return settings;
  }

  public RoomStatus getStatus() {
    return status;
  }

  public UUID getHostPublicId() {
    return hostPublicId;
  }

  public Map<UUID, RoomPlayer> getPlayers() {
    return Collections.unmodifiableMap(players);
  }

  public List<RoomPlayer> getPlayerList() {
    return new ArrayList<>(players.values());
  }

  public int getPlayerCount() {
    return players.size();
  }

  public ReentrantLock getLock() {
    return lock;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public ScheduledFuture<?> getCountdownFuture() {
    return countdownFuture;
  }

  public void setCountdownFuture(ScheduledFuture<?> countdownFuture) {
    this.countdownFuture = countdownFuture;
  }

  public record RemoveResult(boolean removed, UUID newHostPublicId, boolean isEmpty) {}
}
