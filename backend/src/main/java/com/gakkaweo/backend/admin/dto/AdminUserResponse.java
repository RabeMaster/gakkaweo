package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
    UUID publicId,
    String nickname,
    String profileUrl,
    String role,
    Boolean banned,
    Instant bannedAt,
    String provider,
    Instant createdAt) {

  public static AdminUserResponse from(Member member, String provider) {
    return new AdminUserResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name(),
        member.getBanned(),
        member.getBannedAt(),
        provider,
        member.getCreatedAt());
  }
}
