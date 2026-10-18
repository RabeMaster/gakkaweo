import { apiFetch } from "@/shared/api/client";
import type {
  GuessRequest,
  GuessResponse,
  HintResponse,
  HistoryResponse,
  StatusResponse,
  TodayResponse,
  YesterdayMeResponse,
} from "@/shared/api/types";

export function getToday(signal?: AbortSignal) {
  return apiFetch<TodayResponse>("/daily/today", { signal });
}

export function submitGuess(body: GuessRequest) {
  return apiFetch<GuessResponse>("/daily/guess", {
    method: "POST",
    body: body,
  });
}

export function getHistory(sentenceId: string, signal?: AbortSignal) {
  return apiFetch<HistoryResponse>(`/daily/history?sentenceId=${sentenceId}`, { signal });
}

export function getStatus(signal?: AbortSignal) {
  return apiFetch<StatusResponse>("/daily/status", { signal });
}

export function getHints(sentenceId: string, signal?: AbortSignal) {
  return apiFetch<HintResponse>(`/daily/hints?sentenceId=${sentenceId}`, { signal });
}

export function getYesterdayMe(signal?: AbortSignal) {
  return apiFetch<YesterdayMeResponse>("/daily/yesterday/me", { signal });
}
