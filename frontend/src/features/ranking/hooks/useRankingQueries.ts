import { useQuery } from "@tanstack/react-query";
import { getRankings } from "@/features/ranking/api";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { STALE_TIME } from "@/shared/config/query";

export function useRankings(sentenceId: string | undefined) {
  const { user, sessionGeneration, isLoading } = useAuthStore();
  return useQuery({
    queryKey: ["ranking", sentenceId, user?.publicId, sessionGeneration],
    queryFn: ({ signal }) => getRankings(sentenceId!, signal),
    staleTime: STALE_TIME.SHORT,
    enabled: !!sentenceId && !isLoading,
    retry: false,
    refetchInterval: (query) => (query.state.error ? 15_000 : false),
  });
}
