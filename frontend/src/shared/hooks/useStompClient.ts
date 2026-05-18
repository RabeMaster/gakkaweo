import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import type { IMessage, StompSubscription } from "@stomp/stompjs";
import { WS_URL } from "@/shared/config/env";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";

const INITIAL_RECONNECT_DELAY = 2_000;
const MAX_RECONNECT_DELAY = 30_000;

export function useStompClient() {
  const clientRef = useRef<Client | null>(null);
  const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY);
  const setWsConnected = useConnectionStore((s) => s.setWsConnected);

  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      heartbeatIncoming: 25_000,
      heartbeatOutgoing: 25_000,
      reconnectDelay: INITIAL_RECONNECT_DELAY,
      beforeConnect: () => {
        client.reconnectDelay = reconnectDelayRef.current;
      },
      onConnect: () => {
        reconnectDelayRef.current = INITIAL_RECONNECT_DELAY;
        setWsConnected(true);
      },
      onDisconnect: () => {
        setWsConnected(false);
      },
      onStompError: () => {
        setWsConnected(false);
      },
      onWebSocketClose: () => {
        setWsConnected(false);
        reconnectDelayRef.current = Math.min(reconnectDelayRef.current * 2, MAX_RECONNECT_DELAY);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      setWsConnected(false);
    };
  }, [setWsConnected]);

  function subscribe(destination: string, callback: (message: IMessage) => void): StompSubscription | undefined {
    return clientRef.current?.subscribe(destination, callback);
  }

  function publish(destination: string, body: string) {
    clientRef.current?.publish({ destination, body });
  }

  return { subscribe, publish, clientRef };
}
