package com.gakkaweo.backend.admin.dto;

import com.gakkaweo.backend.domain.admin.entity.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
    Long id,
    String adminNickname,
    String action,
    String targetType,
    String targetId,
    String detail,
    String ipAddress,
    Instant createdAt) {

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
