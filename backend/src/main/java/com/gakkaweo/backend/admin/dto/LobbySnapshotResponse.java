package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.multiplayer.room.dto.LobbyRoomInfo;
import java.util.List;

public record LobbySnapshotResponse(int totalRooms, int totalPlayers, List<LobbyRoomInfo> rooms) {

  public static LobbySnapshotResponse from(List<LobbyRoomInfo> rooms, int totalPlayers) {
    return new LobbySnapshotResponse(rooms.size(), totalPlayers, rooms);
  }
}
