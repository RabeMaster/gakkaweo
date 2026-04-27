import { apiFetch } from "@/shared/api/client";
import type {
  AdminUserResponse,
  AnnouncementResponse,
  AuditLogPage,
  CsvUploadResponse,
  DateStatsResponse,
  DuplicateCheckResponse,
  FullRankingResponse,
  GuessLogResponse,
  SentenceListResponse,
  SentenceResponse,
  SentenceStatsResponse,
  SimilarityTestResponse,
  SystemStatusResponse,
  TodayWidgetResponse,
  TrendDataResponse,
  UserDetailResponse,
  UserGameHistoryResponse,
  UserListResponse,
} from "@/features/admin/types";

// --- Sentence ---

export function getSentences(status?: string, sort?: string, page = 0, size = 20) {
  const params = new URLSearchParams();
  if (status) {
    params.set("status", status);
  }
  if (sort) {
    params.set("sort", sort);
  }
  params.set("page", String(page));
  params.set("size", String(size));
  return apiFetch<SentenceListResponse>(`/admin/sentences?${params}`);
}

export function getSentence(publicId: string) {
  return apiFetch<SentenceResponse>(`/admin/sentences/${publicId}`);
}

export function createSentence(sentence: string) {
  return apiFetch<SentenceResponse>("/admin/sentences", { method: "POST", body: { sentence } });
}

export function updateSentence(publicId: string, sentence: string) {
  return apiFetch<SentenceResponse>(`/admin/sentences/${publicId}`, { method: "PATCH", body: { sentence } });
}

export function deleteSentence(publicId: string) {
  return apiFetch<void>(`/admin/sentences/${publicId}`, { method: "DELETE" });
}

export function getSentenceStats(publicId: string) {
  return apiFetch<SentenceStatsResponse>(`/admin/sentences/${publicId}/stats`);
}

export function getUnusedCount() {
  return apiFetch<{ count: number }>("/admin/sentences/unused-count");
}

export function uploadCsv(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<CsvUploadResponse>("/admin/sentences/upload", { method: "POST", body: formData });
}

export function scheduleSentence(publicId: string, date: string) {
  return apiFetch<SentenceResponse>(`/admin/sentences/${publicId}/schedule`, { method: "POST", body: { date } });
}

export function unscheduleSentence(publicId: string) {
  return apiFetch<SentenceResponse>(`/admin/sentences/${publicId}/schedule`, { method: "DELETE" });
}

export function testSimilarity(sentence: string, guessText: string) {
  return apiFetch<SimilarityTestResponse>("/admin/sentences/similarity-test", {
    method: "POST",
    body: { sentence, guessText },
  });
}

export function checkDuplicate(sentence: string) {
  return apiFetch<DuplicateCheckResponse>("/admin/sentences/duplicate-check", {
    method: "POST",
    body: { sentence },
  });
}

export function emergencyReplace(newSentencePublicId: string, returnOldToPool: boolean) {
  return apiFetch<SentenceResponse>("/admin/sentences/emergency-replace", {
    method: "POST",
    body: { newSentencePublicId, returnOldToPool },
  });
}

// --- User ---

export function getUsers(nickname?: string, banned?: boolean, sort?: string, page = 0, size = 20) {
  const params = new URLSearchParams();
  if (nickname) {
    params.set("nickname", nickname);
  }
  if (banned !== undefined) {
    params.set("banned", String(banned));
  }
  if (sort) {
    params.set("sort", sort);
  }
  params.set("page", String(page));
  params.set("size", String(size));
  return apiFetch<UserListResponse>(`/admin/users?${params}`);
}

export function getUserDetail(publicId: string) {
  return apiFetch<UserDetailResponse>(`/admin/users/${publicId}`);
}

export function getUserHistory(publicId: string) {
  return apiFetch<UserGameHistoryResponse>(`/admin/users/${publicId}/history`);
}

export function changeRole(publicId: string, role: string) {
  return apiFetch<AdminUserResponse>(`/admin/users/${publicId}/role`, { method: "PATCH", body: { role } });
}

export function banUser(publicId: string) {
  return apiFetch<void>(`/admin/users/${publicId}/ban`, { method: "POST" });
}

export function unbanUser(publicId: string) {
  return apiFetch<void>(`/admin/users/${publicId}/ban`, { method: "DELETE" });
}

export function forceDeleteUser(publicId: string) {
  return apiFetch<void>(`/admin/users/${publicId}`, { method: "DELETE" });
}

export function forceChangeNickname(publicId: string, nickname: string) {
  return apiFetch<AdminUserResponse>(`/admin/users/${publicId}/nickname`, { method: "PATCH", body: { nickname } });
}

export function forceDeleteProfileImage(publicId: string) {
  return apiFetch<void>(`/admin/users/${publicId}/profile-image`, { method: "DELETE" });
}

// --- Dashboard ---

export function getTodayWidget() {
  return apiFetch<TodayWidgetResponse>("/admin/dashboard/today");
}

export function getFullRanking(date?: string) {
  const params = date ? `?date=${date}` : "";
  return apiFetch<FullRankingResponse>(`/admin/dashboard/ranking${params}`);
}

export function getDateStats(date: string) {
  return apiFetch<DateStatsResponse>(`/admin/dashboard/stats/${date}`);
}

export function getTrends(days = 30) {
  return apiFetch<TrendDataResponse>(`/admin/dashboard/trends?days=${days}`);
}

export function getGuessLog(date: string, memberPublicId?: string) {
  const params = new URLSearchParams({ date });
  if (memberPublicId) {
    params.set("memberPublicId", memberPublicId);
  }
  return apiFetch<GuessLogResponse>(`/admin/dashboard/guess-log?${params}`);
}

// --- System ---

export function getAnnouncements() {
  return apiFetch<AnnouncementResponse[]>("/admin/system/announcements");
}

export function createAnnouncement(body: {
  title: string;
  content?: string;
  type: string;
  startsAt: string;
  endsAt?: string;
}) {
  return apiFetch<AnnouncementResponse>("/admin/system/announcements", { method: "POST", body });
}

export function updateAnnouncement(
  id: number,
  body: { title?: string; content?: string; type?: string; active?: boolean; startsAt?: string; endsAt?: string },
) {
  return apiFetch<AnnouncementResponse>(`/admin/system/announcements/${id}`, { method: "PATCH", body });
}

export function deleteAnnouncement(id: number) {
  return apiFetch<void>(`/admin/system/announcements/${id}`, { method: "DELETE" });
}

export function getSystemStatus() {
  return apiFetch<SystemStatusResponse>("/admin/system/status");
}

export function resetRankingCache() {
  return apiFetch<void>("/admin/system/ranking-cache/reset", { method: "POST" });
}

export function resetRateLimit() {
  return apiFetch<void>("/admin/system/rate-limit/reset", { method: "POST" });
}

export function getAuditLogs(action?: string, dateFrom?: string, dateTo?: string, sort?: string, page = 0, size = 20) {
  const params = new URLSearchParams();
  if (action) {
    params.set("action", action);
  }
  if (dateFrom) {
    params.set("dateFrom", dateFrom);
  }
  if (dateTo) {
    params.set("dateTo", dateTo);
  }
  if (sort) {
    params.set("sort", sort);
  }
  params.set("page", String(page));
  params.set("size", String(size));
  return apiFetch<AuditLogPage>(`/admin/system/audit-logs?${params}`);
}
