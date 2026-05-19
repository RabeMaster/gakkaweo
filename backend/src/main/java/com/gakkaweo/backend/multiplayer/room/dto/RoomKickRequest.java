package com.gakkaweo.backend.multiplayer.room.dto;

import java.util.UUID;

public record RoomKickRequest(UUID targetPublicId) {}
