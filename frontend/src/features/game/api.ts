import { apiFetch } from "@/shared/api/client";
import type { GuessRequest, GuessResponse, HistoryResponse, StatusResponse, TodayResponse } from "@/shared/api/types";

export function getToday() {
  return apiFetch<TodayResponse>("/daily/today");
}

export function submitGuess(body: GuessRequest) {
  return apiFetch<GuessResponse>("/daily/guess", {
    method: "POST",
    body: body as unknown as BodyInit,
  });
}

export function getHistory(sentenceId: string) {
  return apiFetch<HistoryResponse>(`/daily/history?sentenceId=${sentenceId}`);
}

export function getStatus() {
  return apiFetch<StatusResponse>("/daily/status");
}
