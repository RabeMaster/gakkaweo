import { create } from "zustand";

interface ConnectionState {
  sseConnectionCount: number | null;
  setSseConnectionCount: (count: number) => void;
  wsConnected: boolean;
  setWsConnected: (connected: boolean) => void;
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  sseConnectionCount: null,
  setSseConnectionCount: (count) => set({ sseConnectionCount: count }),
  wsConnected: false,
  setWsConnected: (connected) => set({ wsConnected: connected }),
}));
