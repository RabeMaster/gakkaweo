import { useEffect, useState } from "react";
import { useStompClient } from "@/shared/hooks/useStompClient";
import type { LobbyRoomInfo, WsNotification } from "../types";

export function useLobbySubscription() {
  const { subscribe } = useStompClient();
  const [rooms, setRooms] = useState<LobbyRoomInfo[]>([]);

  useEffect(() => {
    const sub = subscribe("/topic/lobby", (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      if (notification.type === "LOBBY_UPDATE") {
        setRooms(notification.payload as LobbyRoomInfo[]);
      }
    });
    return () => {
      sub?.unsubscribe();
    };
  }, [subscribe]);

  return rooms;
}
