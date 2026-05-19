package com.gakkaweo.backend.multiplayer.room;

public record RoomStateChangedEvent(String roomId, RoomChangeType type) {}
