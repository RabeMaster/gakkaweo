import { useCallback } from "react";
import { useStompPublish } from "@/shared/hooks/useStompClient";
import type { GameMode } from "../types";

interface CreateRoomParams {
  title: string;
  mode: GameMode;
  rounds: number;
  timeLimit: number;
  maxPlayers: number;
  password: string | null;
  guessPublic: boolean;
}

export function useRoomActions() {
  const publish = useStompPublish();

  const createRoom = useCallback(
    (params: CreateRoomParams) => {
      publish("/app/room/create", JSON.stringify(params));
    },
    [publish],
  );

  const joinRoom = useCallback(
    (roomId: string, password: string | null) => {
      publish(`/app/room/${roomId}/join`, JSON.stringify({ password }));
    },
    [publish],
  );

  const quickJoin = useCallback(() => {
    publish("/app/lobby/quick", "{}");
  }, [publish]);

  const leaveRoom = useCallback(
    (roomId: string) => {
      publish(`/app/room/${roomId}/leave`, "{}");
    },
    [publish],
  );

  const toggleReady = useCallback(
    (roomId: string) => {
      publish(`/app/room/${roomId}/ready`, "{}");
    },
    [publish],
  );

  const startGame = useCallback(
    (roomId: string) => {
      publish(`/app/room/${roomId}/start`, "{}");
    },
    [publish],
  );

  const updateSettings = useCallback(
    (roomId: string, params: CreateRoomParams) => {
      publish(`/app/room/${roomId}/settings`, JSON.stringify(params));
    },
    [publish],
  );

  const kickPlayer = useCallback(
    (roomId: string, targetPublicId: string) => {
      publish(`/app/room/${roomId}/kick`, JSON.stringify({ targetPublicId }));
    },
    [publish],
  );

  const delegateHost = useCallback(
    (roomId: string, targetPublicId: string) => {
      publish(`/app/room/${roomId}/delegate`, JSON.stringify({ targetPublicId }));
    },
    [publish],
  );

  return {
    createRoom,
    joinRoom,
    quickJoin,
    leaveRoom,
    toggleReady,
    startGame,
    updateSettings,
    kickPlayer,
    delegateHost,
  };
}
