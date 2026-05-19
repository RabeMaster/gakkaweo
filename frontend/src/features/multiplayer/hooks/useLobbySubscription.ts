import { useEffect, useState } from "react";
import { useStompSubscribe } from "@/shared/hooks/useStompClient";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import type { LobbyRoomInfo, WsNotification } from "../types";

export function useLobbySubscription() {
  const subscribe = useStompSubscribe();
  const wsConnected = useConnectionStore((s) => s.wsConnected);
  const [rooms, setRooms] = useState<LobbyRoomInfo[]>([]);

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
