import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { RankingResponse } from "@/shared/api/types";
import { API_BASE_URL } from "@/shared/config/env";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";

const SSE_URL = `${API_BASE_URL}/ranking/stream`;
const INITIAL_DELAY = 2000;
const MAX_DELAY = 30_000;

export function useRankingSSE() {
  const queryClient = useQueryClient();
  const retryDelay = useRef(INITIAL_DELAY);
  const retryTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let es: EventSource | null = null;
    let unmounted = false;

    function connect() {
      if (unmounted) {
        return;
      }

      es = new EventSource(SSE_URL, { withCredentials: true });

      es.addEventListener("RANKING_UPDATE", (e: MessageEvent) => {
        const data = JSON.parse(e.data) as Pick<RankingResponse, "rankings" | "totalPlayers">;
        queryClient.setQueryData<RankingResponse>(["ranking"], (old) => {
          if (!old) {
            return old;
          }
          return { ...old, rankings: data.rankings, totalPlayers: data.totalPlayers };
        });
        retryDelay.current = INITIAL_DELAY;
      });

      es.addEventListener("DAY_CHANGE", () => {
        queryClient.removeQueries({ queryKey: ["game"] });
        queryClient.invalidateQueries({ queryKey: ["ranking"] });
      });

      es.addEventListener("ANNOUNCEMENT", () => {
        window.dispatchEvent(new CustomEvent("sse:announcement"));
      });

      es.addEventListener("HEARTBEAT", (e: MessageEvent) => {
        if (!e.data) {
          return;
        }
        try {
          const data = JSON.parse(e.data) as { sseConnectionCount?: number };
          if (data.sseConnectionCount != null) {
            useConnectionStore.getState().setSseConnectionCount(data.sseConnectionCount);
          }
        } catch {
          // 빈 문자열 등 파싱 실패 시 무시 (하위 호환)
        }
      });

      es.onerror = () => {
        es?.close();
        if (!unmounted) {
          retryTimeout.current = setTimeout(connect, retryDelay.current);
          retryDelay.current = Math.min(retryDelay.current * 2, MAX_DELAY);
        }
      };
    }

    connect();

    return () => {
      unmounted = true;
      es?.close();
      if (retryTimeout.current) {
        clearTimeout(retryTimeout.current);
      }
    };
  }, [queryClient]);
}
