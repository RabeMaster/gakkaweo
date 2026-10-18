import { apiFetch } from "@/shared/api/client";
import type { RankingResponse } from "@/shared/api/types";

export function getRankings(sentenceId: string, signal?: AbortSignal) {
  return apiFetch<RankingResponse>(`/ranking/today?sentenceId=${encodeURIComponent(sentenceId)}`, { signal });
}
