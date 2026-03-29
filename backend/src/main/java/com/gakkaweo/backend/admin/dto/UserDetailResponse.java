package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.time.Instant;
import java.util.UUID;

public record UserDetailResponse(
    UUID publicId,
    String nickname,
    String profileUrl,
    String role,
    Boolean banned,
    Instant bannedAt,
    String provider,
    Instant createdAt,
    ActivitySummary activity) {

  public static UserDetailResponse from(Member member, String provider, ActivitySummary activity) {
    return new UserDetailResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name(),
        member.getBanned(),
        member.getBannedAt(),
        provider,
        member.getCreatedAt(),
        activity);
  }

  public record ActivitySummary(
      long totalParticipations, long totalClears, double avgAttemptCount, Integer bestRank) {}
}
