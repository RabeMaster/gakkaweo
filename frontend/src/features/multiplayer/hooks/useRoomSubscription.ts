import { useEffect } from "react";
import { useStompSubscribe } from "@/shared/hooks/useStompClient";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import type { RoomSnapshot, WsNotification } from "../types";
import { useRoomStore } from "../stores/useRoomStore";

export function useRoomSubscription(roomId: string | undefined) {
  const subscribe = useStompSubscribe();
  const wsConnected = useConnectionStore((s) => s.wsConnected);
  const setRoom = useRoomStore((s) => s.setRoom);
  const updateStatus = useRoomStore((s) => s.updateStatus);

  useEffect(() => {
    if (!roomId || !wsConnected) {
      return;
    }

    const roomSub = subscribe(`/topic/room/${roomId}`, (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      const snapshot = notification.payload as RoomSnapshot;
      if (
        notification.type === "PLAYER_JOINED" ||
        notification.type === "PLAYER_LEFT" ||
        notification.type === "PLAYER_READY" ||
        notification.type === "PLAYER_KICKED" ||
        notification.type === "HOST_DELEGATED" ||
        notification.type === "ROOM_SETTINGS_UPDATED" ||
        notification.type === "COUNTDOWN_CANCELLED"
      ) {
        setRoom(snapshot);
      }
    });

    const gameSub = subscribe(`/topic/room/${roomId}/game`, (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      if (notification.type === "COUNTDOWN") {
        updateStatus("COUNTDOWN");
      }
      if (notification.type === "GAME_STARTED") {
        const snapshot = notification.payload as RoomSnapshot;
        setRoom(snapshot);
      }
    });

    return () => {
      roomSub?.unsubscribe();
      gameSub?.unsubscribe();
    };
  }, [roomId, subscribe, wsConnected, setRoom, updateStatus]);
}
