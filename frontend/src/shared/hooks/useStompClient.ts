import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import type { IMessage, StompSubscription } from "@stomp/stompjs";
import { WS_URL } from "@/shared/config/env";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";

const INITIAL_RECONNECT_DELAY = 2_000;
const MAX_RECONNECT_DELAY = 30_000;

let sharedClient: Client | null = null;

export function useStompClient() {
  const reconnectDelayRef = useRef(INITIAL_RECONNECT_DELAY);
  const setWsConnected = useConnectionStore((s) => s.setWsConnected);

  useEffect(() => {
    if (sharedClient) {
      return;
    }

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

    sharedClient = client;
    client.activate();

    return () => {
      client.deactivate();
      sharedClient = null;
      setWsConnected(false);
    };
  }, [setWsConnected]);

  return { subscribe: stompSubscribe, publish: stompPublish };
}

export function useStompSubscribe() {
  return stompSubscribe;
}

export function useStompPublish() {
  return stompPublish;
}

function stompSubscribe(destination: string, callback: (message: IMessage) => void): StompSubscription | undefined {
  return sharedClient?.subscribe(destination, callback);
}

function stompPublish(destination: string, body: string) {
  sharedClient?.publish({ destination, body });
}
