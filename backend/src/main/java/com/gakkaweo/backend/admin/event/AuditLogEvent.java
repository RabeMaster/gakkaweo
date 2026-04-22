package com.gakkaweo.backend.admin.event;

import java.time.Instant;

public record AuditLogEvent(
    String action,
    String targetType,
    String targetId,
    String adminNickname,
    String detail,
    String ipAddress,
    Instant createdAt) {}
