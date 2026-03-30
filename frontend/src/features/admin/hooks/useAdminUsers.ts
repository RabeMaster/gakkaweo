import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  banUser,
  changeRole,
  forceChangeNickname,
  forceDeleteProfileImage,
  forceDeleteUser,
  getUserDetail,
  getUserHistory,
  getUsers,
  unbanUser,
} from "@/features/admin/api";

export function useUsers(nickname?: string, banned?: boolean, page = 0, size = 20) {
  return useQuery({
    queryKey: ["admin", "users", nickname, banned, page, size],
    queryFn: () => getUsers(nickname, banned, page, size),
  });
}

export function useUserDetail(publicId: string | null) {
  return useQuery({
    queryKey: ["admin", "users", publicId, "detail"],
    queryFn: () => getUserDetail(publicId!),
    enabled: !!publicId,
  });
}

export function useUserHistory(publicId: string | null) {
  return useQuery({
    queryKey: ["admin", "users", publicId, "history"],
    queryFn: () => getUserHistory(publicId!),
    enabled: !!publicId,
  });
}

export function useChangeRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ publicId, role }: { publicId: string; role: string }) => changeRole(publicId, role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useBanUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => banUser(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useUnbanUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => unbanUser(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useForceDeleteUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => forceDeleteUser(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useForceChangeNickname() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ publicId, nickname }: { publicId: string; nickname: string }) =>
      forceChangeNickname(publicId, nickname),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useForceDeleteProfileImage() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (publicId: string) => forceDeleteProfileImage(publicId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}
