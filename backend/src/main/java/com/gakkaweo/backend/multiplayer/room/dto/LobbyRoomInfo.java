package com.gakkaweo.backend.multiplayer.room.dto;

import com.gakkaweo.backend.multiplayer.room.GameMode;
import com.gakkaweo.backend.multiplayer.room.Room;
import com.gakkaweo.backend.multiplayer.room.RoomPlayer;
import com.gakkaweo.backend.multiplayer.room.RoomStatus;

public record LobbyRoomInfo(
    String roomId,
    String title,
    GameMode mode,
    int playerCount,
    int maxPlayers,
    RoomStatus status,
    boolean hasPassword,
    String hostNickname) {

  public static LobbyRoomInfo from(Room room) {
    RoomPlayer host = room.getPlayers().get(room.getHostPublicId());
    String hostNick = host != null ? host.getNickname() : "";
    return new LobbyRoomInfo(
        room.getId(),
        room.getSettings().title(),
        room.getSettings().mode(),
        room.getPlayerCount(),
        room.getSettings().maxPlayers(),
        room.getStatus(),
        room.getSettings().hasPassword(),
        hostNick);
  }
}
