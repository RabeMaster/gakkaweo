import { create } from "zustand";
import { apiFetch } from "@/shared/api/client";
import { queryClient } from "@/shared/api/queryClient";
import type { MeResponse } from "@/shared/api/types";

interface AuthState {
  user: MeResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  fetchUser: () => Promise<void>;
  clearUser: () => void;
  updateUser: (patch: Partial<MeResponse>) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  fetchUser: async () => {
    try {
      const user = await apiFetch<MeResponse>("/auth/me");
      if (user?.publicId) {
        set({ user, isAuthenticated: true, isLoading: false });
      } else {
        set({ user: null, isAuthenticated: false, isLoading: false });
      }
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },
  clearUser: () => {
    queryClient.clear();
    set({ user: null, isAuthenticated: false, isLoading: false });
  },
  updateUser: (patch) => {
    set((state) => {
      if (!state.user) {
        return state;
      }
      return { user: { ...state.user, ...patch } };
    });
  },
}));
