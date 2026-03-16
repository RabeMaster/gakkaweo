package com.gakkaweo.backend.game.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TodayResponse(
    UUID sentenceId,
    String hintMask,
    int wordCount,
    List<Integer> charCounts,
    LocalDateTime expiresAt) {}
