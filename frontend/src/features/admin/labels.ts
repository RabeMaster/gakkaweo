export const AUDIT_ACTION_LABELS: Record<string, string> = {
  SENTENCE_CREATE: "문장 생성",
  SENTENCE_UPDATE: "문장 수정",
  SENTENCE_DELETE: "문장 삭제",
  SENTENCE_SCHEDULE: "문장 예약",
  SENTENCE_UNSCHEDULE: "문장 예약 취소",
  EMERGENCY_REPLACE: "문장 긴급 교체",
  CSV_UPLOAD: "문장 CSV 업로드",
  ROLE_CHANGE: "회원 역할 변경",
  USER_BAN: "회원 차단",
  USER_UNBAN: "회원 차단 해제",
  USER_FORCE_DELETE: "회원 강제 탈퇴",
  USER_FORCE_NICKNAME: "회원 닉네임 강제 변경",
  USER_FORCE_PROFILE_DELETE: "회원 프로필 강제 삭제",
  ANNOUNCEMENT_CREATE: "공지 생성",
  ANNOUNCEMENT_UPDATE: "공지 수정",
  ANNOUNCEMENT_DELETE: "공지 삭제",
  RANKING_CACHE_RESET: "랭킹 캐시 초기화",
  RATE_LIMIT_RESET: "Rate Limit 초기화",
};

export const AUDIT_TARGET_TYPE_LABELS: Record<string, string> = {
  SENTENCE: "문장",
  MEMBER: "회원",
  ANNOUNCEMENT: "공지",
  SYSTEM: "시스템",
};

export function getAuditActionLabel(action: string): string {
  return AUDIT_ACTION_LABELS[action] ?? action;
}

export function getAuditTargetTypeLabel(type: string): string {
  return AUDIT_TARGET_TYPE_LABELS[type] ?? type;
}
