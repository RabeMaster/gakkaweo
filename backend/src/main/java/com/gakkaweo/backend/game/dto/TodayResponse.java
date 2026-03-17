package com.gakkaweo.backend.game.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TodayResponse(
    UUID sentenceId, String hintMask, int wordCount, List<Integer> charCounts, Instant expiresAt) {}
