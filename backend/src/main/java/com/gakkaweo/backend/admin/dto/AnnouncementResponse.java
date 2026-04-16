package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "공지 정보")
public record AnnouncementResponse(
    @Schema(description = "공지 ID", example = "1") Long id,
    @Schema(description = "작성자 닉네임", example = "관리자") String adminNickname,
    @Schema(description = "제목", example = "서버 점검 안내") String title,
    @Schema(description = "내용", nullable = true, example = "4월 20일 점검 예정입니다") String content,
    @Schema(
            description = "공지 유형",
            allowableValues = {"INFO", "MAINTENANCE", "WARNING"},
            example = "MAINTENANCE")
        String type,
    @Schema(description = "활성 여부", example = "true") boolean active,
    @Schema(description = "시작 시각", example = "2026-04-17T00:00:00Z") Instant startsAt,
    @Schema(description = "종료 시각", nullable = true, example = "2026-04-18T00:00:00Z")
        Instant endsAt,
    @Schema(description = "등록 시각", example = "2026-04-16T12:00:00Z") Instant createdAt) {

  public static AnnouncementResponse from(Announcement entity) {
    String adminNickname = entity.getAdmin() != null ? entity.getAdmin().getNickname() : "(삭제됨)";
    return new AnnouncementResponse(
        entity.getId(),
        adminNickname,
        entity.getTitle(),
        entity.getContent(),
        entity.getType().name(),
        entity.getActive(),
        entity.getStartsAt(),
        entity.getEndsAt(),
        entity.getCreatedAt());
  }
}
