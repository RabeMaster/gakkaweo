package com.gakkaweo.backend.multiplayer.room;

import com.gakkaweo.backend.config.MultiplayerProperties;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomManager {

  private static final String ID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int ID_LENGTH = 6;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, String> memberToRoom = new ConcurrentHashMap<>();

  private final MultiplayerProperties properties;

  public Room createRoom(RoomSettings settings, UUID hostPublicId, String nickname, Clock clock) {
    settings.validate();

    if (rooms.size() >= properties.room().maxConcurrent()) {
      throw RoomException.createFailed(
          RoomException.Reason.ROOM_LIMIT_REACHED, "동시 방 수가 상한에 도달했습니다");
    }

    String existingRoom = memberToRoom.get(hostPublicId);
    if (existingRoom != null) {
      throw RoomException.createFailed(RoomException.Reason.ALREADY_IN_ROOM, "이미 다른 방에 참가 중입니다");
    }

    String roomId = generateUniqueId();
    Room room = new Room(roomId, settings, hostPublicId, clock.instant());
    room.addPlayer(hostPublicId, nickname, clock.instant());
    rooms.put(roomId, room);
    memberToRoom.put(hostPublicId, roomId);
    return room;
  }

  public Room getRoom(String roomId) {
    return rooms.get(roomId);
  }

  public boolean registerMembership(UUID publicId, String roomId) {
    return memberToRoom.putIfAbsent(publicId, roomId) == null;
  }

  public void unregisterMembership(UUID publicId) {
    memberToRoom.remove(publicId);
  }

  public void removeRoom(String roomId) {
    rooms.remove(roomId);
  }

  public String getActiveRoomId(UUID publicId) {
    return memberToRoom.get(publicId);
  }

  public Room findQuickJoinRoom() {
    List<Room> candidates =
        rooms.values().stream()
            .filter(r -> r.getStatus() == RoomStatus.WAITING)
            .filter(r -> !r.getSettings().hasPassword())
            .filter(r -> r.getPlayerCount() < r.getSettings().maxPlayers())
            .toList();

    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
  }

  public void clearAll() {
    rooms.clear();
    memberToRoom.clear();
  }

  public int getRoomCount() {
    return rooms.size();
  }

  public int getActivePlayerCount() {
    return memberToRoom.size();
  }

  private String generateUniqueId() {
    int maxRetries = properties.room().idRetryCount();
    for (int i = 0; i < maxRetries; i++) {
      String id = randomId();
      if (!rooms.containsKey(id)) {
        return id;
      }
    }
    throw RoomException.createFailed(RoomException.Reason.ROOM_LIMIT_REACHED, "방 ID 생성에 실패했습니다");
  }

  private String randomId() {
    StringBuilder sb = new StringBuilder(ID_LENGTH);
    for (int i = 0; i < ID_LENGTH; i++) {
      sb.append(ID_CHARS.charAt(SECURE_RANDOM.nextInt(ID_CHARS.length())));
    }
    return sb.toString();
  }
}
