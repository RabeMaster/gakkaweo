import { useQuery } from "@tanstack/react-query";
import { getRankings } from "@/features/ranking/api";

export function useRankings() {
  return useQuery({
    queryKey: ["ranking"],
    queryFn: getRankings,
    staleTime: 30_000,
  });
}
