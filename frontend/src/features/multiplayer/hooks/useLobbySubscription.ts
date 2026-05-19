import { useEffect, useState } from "react";
import { useStompSubscribe } from "@/shared/hooks/useStompClient";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import { apiFetch } from "@/shared/api/client";
import type { LobbyRoomInfo, WsNotification } from "../types";

export function useLobbySubscription() {
  const subscribe = useStompSubscribe();
  const wsConnected = useConnectionStore((s) => s.wsConnected);
  const [rooms, setRooms] = useState<LobbyRoomInfo[]>([]);

  useEffect(() => {
    apiFetch<LobbyRoomInfo[]>("/multi/lobby")
      .then(setRooms)
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!wsConnected) {
      return;
    }
    const sub = subscribe("/topic/lobby", (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      if (notification.type === "LOBBY_UPDATE") {
        setRooms(notification.payload as LobbyRoomInfo[]);
      }
    });
    return () => {
      sub?.unsubscribe();
    };
  }, [subscribe, wsConnected]);

  return rooms;
}
