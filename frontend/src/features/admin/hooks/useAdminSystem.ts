import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAnnouncement,
  deleteAnnouncement,
  getAnnouncements,
  getAuditLogs,
  getSystemStatus,
  resetRankingCache,
  resetRateLimit,
  updateAnnouncement,
} from "@/features/admin/api";

export function useAnnouncements() {
  return useQuery({
    queryKey: ["admin", "system", "announcements"],
    queryFn: getAnnouncements,
  });
}

export function useCreateAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { title: string; content?: string; type: string; startsAt: string; endsAt?: string }) =>
      createAnnouncement(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system", "announcements"] });
    },
  });
}

export function useUpdateAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      ...body
    }: {
      id: number;
      title?: string;
      content?: string;
      type?: string;
      active?: boolean;
      startsAt?: string;
      endsAt?: string;
    }) => updateAnnouncement(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system", "announcements"] });
    },
  });
}

export function useDeleteAnnouncement() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteAnnouncement(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "system", "announcements"] });
    },
  });
}

export function useSystemStatus() {
  return useQuery({
    queryKey: ["admin", "system", "status"],
    queryFn: getSystemStatus,
    refetchInterval: 15_000,
  });
}

export function useResetRankingCache() {
  return useMutation({ mutationFn: resetRankingCache });
}

export function useResetRateLimit() {
  return useMutation({ mutationFn: resetRateLimit });
}

export function useAuditLogs(action?: string, dateFrom?: string, dateTo?: string, page = 0, size = 20) {
  return useQuery({
    queryKey: ["admin", "system", "audit-logs", action, dateFrom, dateTo, page, size],
    queryFn: () => getAuditLogs(action, dateFrom, dateTo, page, size),
  });
}
