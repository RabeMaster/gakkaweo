package com.gakkaweo.backend.auth.dto;

import java.util.UUID;

public record AuthResponse(UUID publicId, String nickname, String profileUrl, String role) {}
