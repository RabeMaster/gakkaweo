package com.gakkaweo.backend.auth.dto;

import com.gakkaweo.backend.domain.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "인증 응답")
public record AuthResponse(
    @Schema(description = "회원 공개 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID publicId,
    @Schema(description = "닉네임", example = "가까워유저") String nickname,
    @Schema(description = "프로필 이미지 URL", nullable = true, example = "/uploads/profile.webp")
        String profileUrl,
    @Schema(
            description = "역할",
            allowableValues = {"USER", "ADMIN", "SUPERADMIN"},
            example = "USER")
        String role) {

  public static AuthResponse from(Member member) {
    return new AuthResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name());
  }
}
