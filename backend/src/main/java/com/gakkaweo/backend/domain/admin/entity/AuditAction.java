package com.gakkaweo.backend.domain.admin.entity;

public enum AuditAction {
  SENTENCE_CREATE(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),
  SENTENCE_UPDATE(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),
  SENTENCE_DELETE(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),
  SENTENCE_SCHEDULE(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),
  SENTENCE_UNSCHEDULE(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),
  EMERGENCY_REPLACE(AuditTargetType.SENTENCE, AuditSeverity.CRITICAL),
  CSV_UPLOAD(AuditTargetType.SENTENCE, AuditSeverity.ROUTINE),

  ROLE_CHANGE(AuditTargetType.MEMBER, AuditSeverity.CRITICAL),
  USER_BAN(AuditTargetType.MEMBER, AuditSeverity.CRITICAL),
  USER_UNBAN(AuditTargetType.MEMBER, AuditSeverity.CRITICAL),
  USER_FORCE_DELETE(AuditTargetType.MEMBER, AuditSeverity.CRITICAL),
  USER_FORCE_NICKNAME(AuditTargetType.MEMBER, AuditSeverity.ROUTINE),
  USER_FORCE_PROFILE_DELETE(AuditTargetType.MEMBER, AuditSeverity.ROUTINE),

  ANNOUNCEMENT_CREATE(AuditTargetType.ANNOUNCEMENT, AuditSeverity.ROUTINE),
  ANNOUNCEMENT_UPDATE(AuditTargetType.ANNOUNCEMENT, AuditSeverity.ROUTINE),
  ANNOUNCEMENT_DELETE(AuditTargetType.ANNOUNCEMENT, AuditSeverity.ROUTINE),

  RANKING_CACHE_RESET(AuditTargetType.SYSTEM, AuditSeverity.CRITICAL),
  RATE_LIMIT_RESET(AuditTargetType.SYSTEM, AuditSeverity.CRITICAL);

  private final AuditTargetType targetType;
  private final AuditSeverity severity;

  AuditAction(AuditTargetType targetType, AuditSeverity severity) {
    this.targetType = targetType;
    this.severity = severity;
  }

  public AuditTargetType targetType() {
    return targetType;
  }

  public AuditSeverity severity() {
    return severity;
  }
}
