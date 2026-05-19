package com.gakkaweo.backend.multiplayer.room;

public enum RoomChangeType {
  CREATED,
  PLAYER_JOINED,
  PLAYER_LEFT,
  SETTINGS_UPDATED,
  HOST_DELEGATED,
  PLAYER_KICKED,
  STATUS_CHANGED,
  DESTROYED
}
