import { apiFetch } from "@/shared/api/client";
import type { ActiveAnnouncementResponse } from "@/shared/api/types";

export function getActiveAnnouncements() {
  return apiFetch<ActiveAnnouncementResponse[]>("/announcements/active");
}
