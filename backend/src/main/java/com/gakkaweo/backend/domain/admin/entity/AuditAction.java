package com.gakkaweo.backend.domain.admin.entity;

public enum AuditAction {
  SENTENCE_CREATE(AuditTargetType.SENTENCE),
  SENTENCE_UPDATE(AuditTargetType.SENTENCE),
  SENTENCE_DELETE(AuditTargetType.SENTENCE),
  SENTENCE_SCHEDULE(AuditTargetType.SENTENCE),
  SENTENCE_UNSCHEDULE(AuditTargetType.SENTENCE),
  EMERGENCY_REPLACE(AuditTargetType.SENTENCE),
  CSV_UPLOAD(AuditTargetType.SENTENCE),

  ROLE_CHANGE(AuditTargetType.MEMBER),
  USER_BAN(AuditTargetType.MEMBER),
  USER_UNBAN(AuditTargetType.MEMBER),
  USER_FORCE_DELETE(AuditTargetType.MEMBER),
  USER_FORCE_NICKNAME(AuditTargetType.MEMBER),
  USER_FORCE_PROFILE_DELETE(AuditTargetType.MEMBER),

  ANNOUNCEMENT_CREATE(AuditTargetType.ANNOUNCEMENT),
  ANNOUNCEMENT_UPDATE(AuditTargetType.ANNOUNCEMENT),
  ANNOUNCEMENT_DELETE(AuditTargetType.ANNOUNCEMENT),

  RANKING_CACHE_RESET(AuditTargetType.SYSTEM),
  RATE_LIMIT_RESET(AuditTargetType.SYSTEM);

  private final AuditTargetType targetType;

  AuditAction(AuditTargetType targetType) {
    this.targetType = targetType;
  }

  public AuditTargetType targetType() {
    return targetType;
  }
}
