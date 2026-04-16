package com.gakkaweo.backend.dto;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "활성 공지 응답")
public record ActiveAnnouncementResponse(
    @Schema(description = "공지 ID", example = "1") Long id,
    @Schema(description = "제목", example = "서비스 점검 안내") String title,
    @Schema(description = "내용", nullable = true, example = "4월 17일 점검 예정입니다") String content,
    @Schema(
            description = "공지 유형",
            allowableValues = {"INFO", "MAINTENANCE", "WARNING"},
            example = "INFO")
        String type,
    @Schema(description = "시작 시각", example = "2026-04-17T00:00:00Z") Instant startsAt,
    @Schema(description = "종료 시각", nullable = true, example = "2026-04-18T00:00:00Z")
        Instant endsAt) {

  public static ActiveAnnouncementResponse from(Announcement entity) {
    return new ActiveAnnouncementResponse(
        entity.getId(),
        entity.getTitle(),
        entity.getContent(),
        entity.getType().name(),
        entity.getStartsAt(),
        entity.getEndsAt());
  }
}
