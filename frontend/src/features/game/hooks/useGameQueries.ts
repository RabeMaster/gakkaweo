import { useMutation, useQuery } from "@tanstack/react-query";
import { getHints, getHistory, getStatus, getToday, submitGuess } from "@/features/game/api";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { STALE_TIME } from "@/shared/config/query";
import type { GuessRequest } from "@/shared/api/types";

export function useToday() {
  return useQuery({
    queryKey: ["game", "today"],
    queryFn: getToday,
    staleTime: STALE_TIME.IMMUTABLE,
  });
}

export function useGameStatus(sentenceId: string | undefined) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["game", "status", sentenceId],
    queryFn: getStatus,
    staleTime: STALE_TIME.SHORT,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useGameHistory(sentenceId: string | undefined) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["game", "history", sentenceId],
    queryFn: () => getHistory(sentenceId!),
    staleTime: STALE_TIME.NONE,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useHints(sentenceId: string | undefined, bestSimilarity: number) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["game", "hints", sentenceId],
    queryFn: () => getHints(sentenceId!),
    staleTime: STALE_TIME.LONG,
    enabled: isAuthenticated && !!sentenceId && bestSimilarity >= 60,
  });
}

export function useGuess() {
  return useMutation({
    mutationFn: (body: GuessRequest) => submitGuess(body),
  });
}
