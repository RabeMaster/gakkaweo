import { create } from "zustand";
import { apiFetch } from "@/shared/api/client";
import type { MeResponse } from "@/shared/api/types";

interface AuthState {
  user: MeResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  fetchUser: () => Promise<void>;
  clearUser: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,
  fetchUser: async () => {
    try {
      const user = await apiFetch<MeResponse>("/auth/me");
      set({ user, isAuthenticated: true, isLoading: false });
    } catch {
      set({ user: null, isAuthenticated: false, isLoading: false });
    }
  },
  clearUser: () => {
    set({ user: null, isAuthenticated: false, isLoading: false });
  },
}));
