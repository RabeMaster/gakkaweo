package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "감사 로그")
public record AuditLogResponse(
    @Schema(description = "로그 ID", example = "1") Long id,
    @Schema(description = "관리자 닉네임", example = "관리자") String adminNickname,
    @Schema(description = "수행 액션", example = "FORCE_NICKNAME") String action,
    @Schema(description = "대상 유형", example = "MEMBER") String targetType,
    @Schema(
            description = "대상 ID",
            nullable = true,
            example = "550e8400-e29b-41d4-a716-446655440000")
        String targetId,
    @Schema(description = "상세 내용", nullable = true, example = "닉네임 변경: 기존 -> 새닉네임") String detail,
    @Schema(description = "IP 주소", example = "127.0.0.1") String ipAddress,
    @Schema(description = "발생 시각", example = "2026-04-17T06:30:00Z") Instant createdAt) {

  public static AuditLogResponse from(AuditLog entity) {
    String adminNickname = entity.getAdmin() != null ? entity.getAdmin().getNickname() : "(삭제됨)";
    return new AuditLogResponse(
        entity.getId(),
        adminNickname,
        entity.getAction(),
        entity.getTargetType(),
        entity.getTargetId(),
        entity.getDetail(),
        entity.getIpAddress(),
        entity.getCreatedAt());
  }
}
