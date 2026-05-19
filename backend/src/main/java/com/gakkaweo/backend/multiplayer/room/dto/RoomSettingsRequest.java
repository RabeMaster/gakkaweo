package com.gakkaweo.backend.multiplayer.room.dto;

import com.gakkaweo.backend.multiplayer.room.GameMode;

public record RoomSettingsRequest(
    String title,
    GameMode mode,
    int rounds,
    int timeLimit,
    int maxPlayers,
    String password,
    boolean guessPublic) {}
