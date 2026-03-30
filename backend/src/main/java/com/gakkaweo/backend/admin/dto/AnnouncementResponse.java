package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import java.time.Instant;

public record AnnouncementResponse(
    Long id,
    String adminNickname,
    String title,
    String content,
    String type,
    boolean active,
    Instant startsAt,
    Instant endsAt,
    Instant createdAt) {

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
