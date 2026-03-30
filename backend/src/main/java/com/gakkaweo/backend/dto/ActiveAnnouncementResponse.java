package com.gakkaweo.backend.dto;

import com.gakkaweo.backend.domain.admin.entity.Announcement;
import java.time.Instant;

public record ActiveAnnouncementResponse(
    Long id, String title, String content, String type, Instant startsAt, Instant endsAt) {

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
