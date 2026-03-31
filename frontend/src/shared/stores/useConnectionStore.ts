import { create } from "zustand";

interface ConnectionState {
  sseConnectionCount: number | null;
  setSseConnectionCount: (count: number) => void;
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  sseConnectionCount: null,
  setSseConnectionCount: (count) => set({ sseConnectionCount: count }),
}));
