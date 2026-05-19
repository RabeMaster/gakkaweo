import { useEffect } from "react";
import { Client } from "@stomp/stompjs";
import type { IMessage, StompSubscription } from "@stomp/stompjs";
import { WS_URL } from "@/shared/config/env";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";

const INITIAL_RECONNECT_DELAY = 2_000;
const MAX_RECONNECT_DELAY = 30_000;

let sharedClient: Client | null = null;
let reconnectDelay = INITIAL_RECONNECT_DELAY;

export function useStompClient() {
  const setWsConnected = useConnectionStore((s) => s.setWsConnected);

  useEffect(() => {
    if (sharedClient) {
      return;
    }

    let active = true;

    const client = new Client({
      brokerURL: WS_URL,
      heartbeatIncoming: 25_000,
      heartbeatOutgoing: 25_000,
      reconnectDelay: INITIAL_RECONNECT_DELAY,
      beforeConnect: () => {
        client.reconnectDelay = reconnectDelay;
      },
      onConnect: () => {
        if (!active) {
          return;
        }
        reconnectDelay = INITIAL_RECONNECT_DELAY;
        setWsConnected(true);
      },
      onDisconnect: () => {
        if (!active) {
          return;
        }
        setWsConnected(false);
      },
      onStompError: () => {
        if (!active) {
          return;
        }
        setWsConnected(false);
      },
      onWebSocketClose: () => {
        if (!active) {
          return;
        }
        setWsConnected(false);
        reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY);
      },
    });

    sharedClient = client;
    client.activate();

    return () => {
      active = false;
      client.deactivate();
      sharedClient = null;
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
