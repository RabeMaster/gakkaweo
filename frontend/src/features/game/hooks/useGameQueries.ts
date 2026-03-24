import { useMutation, useQuery } from "@tanstack/react-query";
import { getHistory, getStatus, getToday, submitGuess } from "@/features/game/api";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import type { GuessRequest } from "@/shared/api/types";

export function useToday() {
  return useQuery({
    queryKey: ["game", "today"],
    queryFn: getToday,
    staleTime: Infinity,
  });
}

export function useGameStatus(sentenceId: string | undefined) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["game", "status"],
    queryFn: getStatus,
    staleTime: 30_000,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useGameHistory(sentenceId: string | undefined) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  return useQuery({
    queryKey: ["game", "history", sentenceId],
    queryFn: () => getHistory(sentenceId!),
    staleTime: 0,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useGuess() {
  return useMutation({
    mutationFn: (body: GuessRequest) => submitGuess(body),
  });
}
