import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getHints, getHistory, getStatus, getToday, getYesterdayMe, submitGuess } from "@/features/game/api";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { HINT_UNLOCK_THRESHOLD } from "@/shared/config/game";
import { STALE_TIME } from "@/shared/config/query";
import type { GuessRequest } from "@/shared/api/types";

export function useToday() {
  const generation = useAuthStore((s) => s.sessionGeneration);
  return useQuery({
    queryKey: ["game", "today", generation],
    queryFn: ({ signal }) => getToday(signal),
    staleTime: STALE_TIME.IMMUTABLE,
  });
}

export function useGameStatus(sentenceId: string | undefined) {
  const queryClient = useQueryClient();
  const { isAuthenticated, user, sessionGeneration } = useAuthStore();

  return useQuery({
    queryKey: ["game", "status", sentenceId, user?.publicId, sessionGeneration],
    queryFn: async ({ signal }) => {
      const status = await getStatus(signal);
      if (status.sentenceId !== sentenceId) {
        await queryClient.invalidateQueries({ queryKey: ["game", "today"] });
        throw new Error("문제가 변경되었습니다.");
      }
      return status;
    },
    staleTime: STALE_TIME.SHORT,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useGameHistory(sentenceId: string | undefined) {
  const { isAuthenticated, user, sessionGeneration } = useAuthStore();

  return useQuery({
    queryKey: ["game", "history", sentenceId, user?.publicId, sessionGeneration],
    queryFn: ({ signal }) => getHistory(sentenceId!, signal),
    staleTime: STALE_TIME.NONE,
    enabled: isAuthenticated && !!sentenceId,
  });
}

export function useHints(sentenceId: string | undefined, bestSimilarity: number) {
  const { isAuthenticated, user, sessionGeneration } = useAuthStore();

  return useQuery({
    queryKey: ["game", "hints", sentenceId, user?.publicId, sessionGeneration],
    queryFn: ({ signal }) => getHints(sentenceId!, signal),
    staleTime: STALE_TIME.LONG,
    enabled: isAuthenticated && !!sentenceId && bestSimilarity >= HINT_UNLOCK_THRESHOLD,
  });
}

export function useYesterdayMe() {
  const { isAuthenticated, user, sessionGeneration } = useAuthStore();

  return useQuery({
    queryKey: ["game", "yesterday", "me", user?.publicId, sessionGeneration],
    queryFn: ({ signal }) => getYesterdayMe(signal),
    staleTime: STALE_TIME.IMMUTABLE,
    enabled: isAuthenticated,
  });
}

export function useGuess() {
  return useMutation({
    mutationFn: (body: GuessRequest) => submitGuess(body),
  });
}
