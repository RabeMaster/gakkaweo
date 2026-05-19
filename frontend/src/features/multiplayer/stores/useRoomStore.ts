import { create } from "zustand";
import type { PlayerInfo, RoomSettings, RoomSnapshot, RoomStatus } from "../types";

interface RoomState {
  roomId: string | null;
  settings: RoomSettings | null;
  status: RoomStatus | null;
  hostPublicId: string | null;
  players: PlayerInfo[];
  setRoom: (snapshot: RoomSnapshot) => void;
  updateStatus: (status: RoomStatus) => void;
  reset: () => void;
}

export const useRoomStore = create<RoomState>((set) => ({
  roomId: null,
  settings: null,
  status: null,
  hostPublicId: null,
  players: [],
  setRoom: (snapshot) =>
    set({
      roomId: snapshot.roomId,
      settings: snapshot.settings,
      status: snapshot.status,
      hostPublicId: snapshot.hostPublicId,
      players: snapshot.players,
    }),
  updateStatus: (status) => set({ status }),
  reset: () =>
    set({
      roomId: null,
      settings: null,
      status: null,
      hostPublicId: null,
      players: [],
    }),
}));
