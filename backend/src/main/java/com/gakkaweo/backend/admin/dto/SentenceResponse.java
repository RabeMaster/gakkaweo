package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.game.entity.DailySentence;
import com.gakkaweo.backend.domain.game.entity.DailySentenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "문장 정보")
public record SentenceResponse(
    @Schema(description = "문장 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID publicId,
    @Schema(description = "문장 내용", example = "오늘 날씨가 좋다") String sentence,
    @Schema(description = "문장 상태") DailySentenceStatus status,
    @Schema(description = "출제일", nullable = true, example = "2026-04-17") LocalDate usedAt,
    @Schema(description = "스케줄일", nullable = true, example = "2026-04-18") LocalDate scheduledAt,
    @Schema(description = "총 참여자 수", nullable = true, example = "50") Integer totalPlayers,
    @Schema(description = "등록 시각", example = "2026-01-01T00:00:00Z") Instant createdAt) {

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
