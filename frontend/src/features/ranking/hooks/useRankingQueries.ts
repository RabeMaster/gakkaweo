import { useQuery } from "@tanstack/react-query";
import { getRankings } from "@/features/ranking/api";
import { STALE_TIME } from "@/shared/config/query";

export function useRankings() {
  return useQuery({
    queryKey: ["ranking"],
    queryFn: getRankings,
    staleTime: STALE_TIME.SHORT,
  });
}
