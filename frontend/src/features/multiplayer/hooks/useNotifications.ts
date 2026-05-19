import { useEffect } from "react";
import { useStompClient } from "@/shared/hooks/useStompClient";
import type { WsNotification } from "../types";

export function useNotifications(onMessage: (notification: WsNotification) => void) {
  const { subscribe } = useStompClient();

  useEffect(() => {
    const sub = subscribe("/user/queue/notifications", (message) => {
      const notification: WsNotification = JSON.parse(message.body);
      onMessage(notification);
    });
    return () => {
      sub?.unsubscribe();
    };
  }, [subscribe, onMessage]);
}
