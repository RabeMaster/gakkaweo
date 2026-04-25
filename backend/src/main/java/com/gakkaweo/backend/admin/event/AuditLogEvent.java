package com.gakkaweo.backend.admin.event;

import com.gakkaweo.backend.domain.admin.entity.AuditAction;
import com.gakkaweo.backend.domain.admin.entity.AuditTargetType;
import java.time.Instant;

public record AuditLogEvent(
    AuditAction action,
    AuditTargetType targetType,
    String targetId,
    String adminNickname,
    String detail,
    String ipAddress,
    Instant createdAt) {}
