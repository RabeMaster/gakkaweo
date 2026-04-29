package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "사용자 상세 응답")
public record UserDetailResponse(
    @Schema(description = "회원 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID publicId,
    @Schema(description = "닉네임", example = "가까워유저") String nickname,
    @Schema(description = "프로필 이미지 URL", nullable = true, example = "/uploads/profile.webp")
        String profileUrl,
    @Schema(
            description = "역할",
            allowableValues = {"USER", "ADMIN", "SUPERADMIN"},
            example = "USER")
        String role,
    @Schema(description = "차단 여부", example = "false") Boolean banned,
    @Schema(description = "차단 시각", nullable = true, example = "2026-04-17T00:00:00Z")
        Instant bannedAt,
    @Schema(
            description = "인증 프로바이더",
            allowableValues = {"LOCAL", "KAKAO", "GOOGLE", "NAVER"},
            example = "LOCAL")
        String provider,
    @Schema(description = "이메일", nullable = true, example = "user@example.com") String email,
    @Schema(description = "가입 시각", example = "2026-01-01T00:00:00Z") Instant createdAt,
    @Schema(description = "활동 요약") ActivitySummary activity) {

  public static UserDetailResponse from(
      Member member, String provider, String email, ActivitySummary activity) {
    return new UserDetailResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name(),
        member.getBanned(),
        member.getBannedAt(),
        provider,
        email,
        member.getCreatedAt(),
        activity);
  }

  @Schema(description = "활동 통계")
  public record ActivitySummary(
      @Schema(description = "총 참여 횟수", example = "42") long totalParticipations,
      @Schema(description = "총 클리어 횟수", example = "30") long totalClears,
      @Schema(description = "평균 시도 횟수", example = "15.3") double avgAttemptCount,
      @Schema(description = "최고 순위", nullable = true, example = "1") Integer bestRank) {}
}
