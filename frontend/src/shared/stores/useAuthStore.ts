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

let authRequestVersion = 0;

function finishAuth(user: MeResponse | null) {
  if (useAuthStore.getState().user?.publicId !== user?.publicId) {
    queryClient.clear();
  }

  useAuthStore.setState({ user, isAuthenticated: Boolean(user), isLoading: false });
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,
  isLoading: true,

  fetchUser: async () => {
    const version = ++authRequestVersion;

    if (!document.cookie.split(";").some((c) => c.trim().startsWith("has_session="))) {
      finishAuth(null);
      return;
    }

    try {
      const user = await apiFetch<MeResponse>("/auth/me");

      if (version !== authRequestVersion) {
        return;
      }

      if (user?.publicId) {
        finishAuth(user);
      } else {
        finishAuth(null);
      }
    } catch {
      if (version !== authRequestVersion) {
        return;
      }

      finishAuth(null);
    }
  },

  clearUser: () => {
    authRequestVersion++;
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
