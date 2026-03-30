package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SentenceResponse(
    UUID publicId,
    String sentence,
    DailySentenceStatus status,
    LocalDate usedAt,
    LocalDate scheduledAt,
    Integer totalPlayers,
    Instant createdAt) {

  public static SentenceResponse from(DailySentence entity) {
    return new SentenceResponse(
        entity.getPublicId(),
        entity.getSentence(),
        entity.getStatus(),
        entity.getUsedAt(),
        entity.getScheduledAt(),
        entity.getTotalPlayers(),
        entity.getCreatedAt());
  }
}
