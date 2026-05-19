package com.gakkaweo.backend.multiplayer.room.dto;

import com.gakkaweo.backend.multiplayer.room.RoomPlayer;
import java.util.UUID;

public record PlayerInfo(UUID publicId, String nickname, boolean ready, boolean isHost) {

  public static PlayerInfo from(RoomPlayer player, UUID hostPublicId) {
    return new PlayerInfo(
        player.getPublicId(),
        player.getNickname(),
        player.isReady(),
        player.getPublicId().equals(hostPublicId));
  }
}
