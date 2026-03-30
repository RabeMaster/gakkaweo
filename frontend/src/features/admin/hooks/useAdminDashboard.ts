import { useQuery } from "@tanstack/react-query";
import { getDateStats, getFullRanking, getGuessLog, getTodayWidget, getTrends } from "@/features/admin/api";

export function useTodayWidget() {
  return useQuery({
    queryKey: ["admin", "dashboard", "today"],
    queryFn: getTodayWidget,
    refetchInterval: 30_000,
  });
}

export function useFullRanking(date?: string) {
  return useQuery({
    queryKey: ["admin", "dashboard", "ranking", date],
    queryFn: () => getFullRanking(date),
  });
}

export function useDateStats(date: string | null) {
  return useQuery({
    queryKey: ["admin", "dashboard", "stats", date],
    queryFn: () => getDateStats(date!),
    enabled: !!date,
  });
}

export function useTrends(days = 30) {
  return useQuery({
    queryKey: ["admin", "dashboard", "trends", days],
    queryFn: () => getTrends(days),
  });
}

export function useGuessLog(date: string | null, memberPublicId?: string) {
  return useQuery({
    queryKey: ["admin", "dashboard", "guess-log", date, memberPublicId],
    queryFn: () => getGuessLog(date!, memberPublicId),
    enabled: !!date,
  });
}
