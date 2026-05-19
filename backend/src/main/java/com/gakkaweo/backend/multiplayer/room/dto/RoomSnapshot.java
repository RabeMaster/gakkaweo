package com.gakkaweo.backend.multiplayer.room.dto;

import com.gakkaweo.backend.multiplayer.room.Room;
import com.gakkaweo.backend.multiplayer.room.RoomSettings;
import com.gakkaweo.backend.multiplayer.room.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomSnapshot(
    String roomId,
    RoomSettings settings,
    RoomStatus status,
    UUID hostPublicId,
    List<PlayerInfo> players,
    Instant createdAt) {

  public static RoomSnapshot from(Room room) {
    List<PlayerInfo> playerInfos =
        room.getPlayerList().stream().map(p -> PlayerInfo.from(p, room.getHostPublicId())).toList();
    return new RoomSnapshot(
        room.getId(),
        room.getSettings(),
        room.getStatus(),
        room.getHostPublicId(),
        playerInfos,
        room.getCreatedAt());
  }
}
