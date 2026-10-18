import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { API_BASE_URL } from "@/shared/config/env";
import { REFETCH_INTERVAL } from "@/shared/config/query";
import type { TodayResponse } from "@/shared/api/types";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import { parseRankingUpdate } from "@/features/ranking/sseEvent";

const SSE_URL = `${API_BASE_URL}/ranking/stream`;
const INITIAL_DELAY = 2000;
const MAX_DELAY = 30_000;

export function useRankingSSE() {
  const queryClient = useQueryClient();
  const generation = useAuthStore((s) => s.sessionGeneration);
  const retryDelay = useRef(INITIAL_DELAY);
  const retryTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    let es: EventSource | null = null;
    let unmounted = false;
    let refreshTimeout: ReturnType<typeof setTimeout> | null = null;
    const current = () => !unmounted && generation === useAuthStore.getState().sessionGeneration;

    function connect() {
      if (!current()) {
        return;
      }

      es = new EventSource(SSE_URL, { withCredentials: true });
      const source = es;
      const active = () => current() && es === source;

      async function refreshRanking() {
        if (refreshTimeout !== null) {
          clearTimeout(refreshTimeout);
          refreshTimeout = null;
        }
        if (!active()) {
          return;
        }
        try {
          // 연결이 끊긴 동안 문제 교체 이벤트를 놓쳤을 수 있습니다.
          await queryClient.invalidateQueries({ queryKey: ["game", "today", generation] }, { throwOnError: true });
        } catch {
          if (active()) {
            // SSE가 연결된 채로 유지돼도 오늘 문제 조회는 다시 시도합니다.
            refreshTimeout = setTimeout(() => void refreshRanking(), REFETCH_INTERVAL.FAST);
          }
          return;
        }
        if (active()) {
          const today = queryClient.getQueryData<TodayResponse>(["game", "today", generation]);
          if (today) {
            void queryClient.invalidateQueries({ queryKey: ["ranking", today.sentenceId] });
          }
        }
      }

      es.onopen = async () => {
        if (!active()) {
          return;
        }
        retryDelay.current = INITIAL_DELAY;
        await refreshRanking();
      };

      es.addEventListener("RANKING_UPDATE", (e: MessageEvent) => {
        const data = parseRankingUpdate(e.data);
        if (!active() || !data) {
          return;
        }
        queryClient.invalidateQueries({ queryKey: ["ranking"], predicate: (q) => !q.state.error });
        retryDelay.current = INITIAL_DELAY;
      });

      es.addEventListener("DAY_CHANGE", (event: MessageEvent) => {
        if (!active()) {
          return;
        }
        try {
          const value = JSON.parse(event.data);
          if (
            typeof value?.newSentenceId !== "string" ||
            !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value.newSentenceId)
          ) {
            return;
          }
        } catch {
          return;
        }
        queryClient.resetQueries({ queryKey: ["game"] });
        queryClient.removeQueries({ queryKey: ["ranking"] });
        queryClient.invalidateQueries({ queryKey: ["ranking"] });
      });

      es.addEventListener("ANNOUNCEMENT", () => {
        if (!active()) {
          return;
        }
        window.dispatchEvent(new CustomEvent("sse:announcement"));
      });

      es.addEventListener("HEARTBEAT", (e: MessageEvent) => {
        if (!active() || !e.data) {
          return;
        }
        try {
          const data = JSON.parse(e.data) as { sseConnectionCount?: number };
          if (
            typeof data.sseConnectionCount === "number" &&
            Number.isInteger(data.sseConnectionCount) &&
            data.sseConnectionCount >= 0
          ) {
            useConnectionStore.getState().setSseConnectionCount(data.sseConnectionCount);
          }
        } catch {
          // 빈 문자열 등 파싱 실패 시 무시 (하위 호환)
        }
      });

      es.onerror = () => {
        if (!active()) {
          return;
        }
        source.close();
        es = null;
        if (refreshTimeout !== null) {
          clearTimeout(refreshTimeout);
          refreshTimeout = null;
        }
        if (current()) {
          retryTimeout.current = setTimeout(connect, retryDelay.current);
          retryDelay.current = Math.min(retryDelay.current * 2, MAX_DELAY);
        }
      };
    }

    connect();

    return () => {
      unmounted = true;
      es?.close();
      if (refreshTimeout !== null) {
        clearTimeout(refreshTimeout);
      }
      if (retryTimeout.current) {
        clearTimeout(retryTimeout.current);
      }
    };
  }, [queryClient, generation]);
}
