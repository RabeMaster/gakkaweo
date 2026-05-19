import { useEffect } from "react";
import { useStompSubscribe } from "@/shared/hooks/useStompClient";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import type { WsNotification } from "../types";

export function useNotifications(onMessage: (notification: WsNotification) => void) {
  const subscribe = useStompSubscribe();
  const wsConnected = useConnectionStore((s) => s.wsConnected);

  useEffect(() => {
    if (!wsConnected) {
      return;
    }
    const sub = subscribe("/user/queue/notifications", (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      onMessage(notification);
    });
    return () => {
      sub?.unsubscribe();
    };
  }, [subscribe, wsConnected, onMessage]);
}
