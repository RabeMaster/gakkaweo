import { apiFetch } from "@/shared/api/client";
import type { RankingResponse } from "@/shared/api/types";

export function getRankings() {
  return apiFetch<RankingResponse>("/ranking/today");
}
