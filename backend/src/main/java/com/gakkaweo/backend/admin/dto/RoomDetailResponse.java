package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.multiplayer.room.GameMode;
import com.gakkaweo.backend.multiplayer.room.Room;
import com.gakkaweo.backend.multiplayer.room.RoomStatus;
import com.gakkaweo.backend.multiplayer.room.dto.PlayerInfo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RoomDetailResponse(
    String roomId,
    String title,
    GameMode mode,
    int rounds,
    int timeLimit,
    int maxPlayers,
    boolean hasPassword,
    boolean guessPublic,
    RoomStatus status,
    UUID hostPublicId,
    String hostNickname,
    List<PlayerInfo> players,
    Instant createdAt) {

  public static RoomDetailResponse from(Room room) {
    List<PlayerInfo> playerInfos =
        room.getPlayerList().stream().map(p -> PlayerInfo.from(p, room.getHostPublicId())).toList();
    var host = room.getPlayers().get(room.getHostPublicId());
    String hostNick = host != null ? host.getNickname() : "";
    return new RoomDetailResponse(
        room.getId(),
        room.getSettings().title(),
        room.getSettings().mode(),
        room.getSettings().rounds(),
        room.getSettings().timeLimit(),
        room.getSettings().maxPlayers(),
        room.getSettings().hasPassword(),
        room.getSettings().guessPublic(),
        room.getStatus(),
        room.getHostPublicId(),
        hostNick,
        playerInfos,
        room.getCreatedAt());
  }
}
