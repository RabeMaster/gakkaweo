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

export function getToday() {
  return apiFetch<TodayResponse>("/daily/today");
}

export function submitGuess(body: GuessRequest) {
  return apiFetch<GuessResponse>("/daily/guess", {
    method: "POST",
    body: body,
  });
}

export function getHistory(sentenceId: string) {
  return apiFetch<HistoryResponse>(`/daily/history?sentenceId=${sentenceId}`);
}

export function getStatus() {
  return apiFetch<StatusResponse>("/daily/status");
}

export function getHints(sentenceId: string) {
  return apiFetch<HintResponse>(`/daily/hints?sentenceId=${sentenceId}`);
}

export function getYesterdayMe() {
  return apiFetch<YesterdayMeResponse>("/daily/yesterday/me");
}
