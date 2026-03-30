package com.gakkaweo.backend.auth.dto;

import com.gakkaweo.backend.domain.member.entity.Member;
import java.util.UUID;

public record AuthResponse(UUID publicId, String nickname, String profileUrl, String role) {

  public static AuthResponse from(Member member) {
    return new AuthResponse(
        member.getPublicId(),
        member.getNickname(),
        member.getProfileUrl(),
        member.getRole().name());
  }
}
