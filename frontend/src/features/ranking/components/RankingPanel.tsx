import { useEffect, useRef, useState } from "react";
import type { TransitionEvent } from "react";
import type { RankingResponse } from "@/shared/api/types";
import { Card } from "@/shared/ui/Card";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useConnectionStore } from "@/shared/stores/useConnectionStore";
import { RankingEntryRow } from "@/features/ranking/components/RankingEntryRow";
import { RankingEmptyState } from "@/features/ranking/components/RankingEmptyState";

const VISIBLE = 5;
const ROTATE_MS = 3000;
const SLIDE_MS = 1000;
const GAP_PX = 6;

function getCircularSlice<T>(items: T[], offset: number, count: number): T[] {
  return Array.from({ length: count }, (_, i) => items[(offset + i) % items.length]);
}

interface RankingPanelProps {
  ranking: RankingResponse | undefined;
  isLoading: boolean;
}

function SkeletonRow() {
  return <div className="h-10 bg-gray-200 dark:bg-gray-800 animate-pulse border-2 border-transparent" />;
}

export function RankingPanel({ ranking, isLoading }: RankingPanelProps) {
  const { user, isAuthenticated } = useAuthStore();
  const sseConnectionCount = useConnectionStore((s) => s.sseConnectionCount);

  const entries = (ranking?.rankings ?? []).slice(0, 10);
  const shouldAnimate = entries.length > VISIBLE;

  const [offset, setOffset] = useState(0);
  const [isSliding, setIsSliding] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [isHovered, setIsHovered] = useState(false);
  const [itemHeight, setItemHeight] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);
  const entriesRef = useRef(entries);
  const [prevEntriesLength, setPrevEntriesLength] = useState(entries.length);

  useEffect(() => {
    entriesRef.current = entries;
  }, [entries]);

  if (entries.length !== prevEntriesLength) {
    setPrevEntriesLength(entries.length);
    if (entries.length === 0) {
      setOffset(0);
      setIsSliding(false);
    }
  }

  useEffect(() => {
    if (!listRef.current?.firstElementChild) {
      return;
    }
    setItemHeight((listRef.current.firstElementChild as HTMLElement).offsetHeight);
  }, [entries.length, isLoading]);

  useEffect(() => {
    if (!shouldAnimate || isPaused || isHovered || itemHeight === 0) {
      return;
    }
    const id = setInterval(() => {
      setIsSliding(true);
    }, ROTATE_MS);
    return () => clearInterval(id);
  }, [shouldAnimate, isPaused, isHovered, itemHeight]);

  const handleTransitionEnd = (e: TransitionEvent<HTMLDivElement>) => {
    if (e.target !== e.currentTarget) {
      return;
    }
    setIsSliding(false);
    setOffset((o) => (o + 1) % entriesRef.current.length);
  };

  const visibleEntries = shouldAnimate ? getCircularSlice(entries, offset, isSliding ? VISIBLE + 1 : VISIBLE) : entries;

  const stride = itemHeight + GAP_PX;
  const containerHeight = shouldAnimate && itemHeight > 0 ? VISIBLE * itemHeight + (VISIBLE - 1) * GAP_PX : undefined;

  const showMySection = isAuthenticated && ranking && entries.length > 0;

  return (
    <Card className="!p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-extrabold">실시간 랭킹</h2>
        {shouldAnimate && (
          <button
            type="button"
            onClick={() => setIsPaused((p) => !p)}
            className="relative group border-2 border-black dark:border-white bg-white dark:bg-gray-900 w-7 h-7 flex items-center justify-center text-[10px] font-black transition-all duration-100 shadow-brutal-sm-hover hover:shadow-none hover:translate-x-[1px] hover:translate-y-[1px]"
            aria-label={isPaused ? "자동 순환 시작" : "자동 순환 정지"}
          >
            {isPaused ? "▶" : "❚❚"}
            <span className="absolute -top-7 left-1/2 -translate-x-1/2 hidden group-hover:block bg-black dark:bg-white text-white dark:text-black text-[10px] font-bold px-1.5 py-0.5 whitespace-nowrap z-50">
              {isPaused ? "재생" : "정지"}
            </span>
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-1.5">
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </div>
      ) : entries.length === 0 ? (
        <RankingEmptyState />
      ) : (
        <div
          className="overflow-hidden"
          style={containerHeight ? { height: containerHeight } : undefined}
          onMouseEnter={() => setIsHovered(true)}
          onMouseLeave={() => setIsHovered(false)}
        >
          <div
            ref={listRef}
            className="flex flex-col"
            style={{
              gap: GAP_PX,
              transform: isSliding ? `translateY(-${stride}px)` : "translateY(0)",
              transition: isSliding ? `transform ${SLIDE_MS}ms ease-in-out` : "none",
            }}
            onTransitionEnd={handleTransitionEnd}
          >
            {visibleEntries.map((entry) => (
              <RankingEntryRow key={entry.publicId} entry={entry} isPaused={isPaused} />
            ))}
          </div>
        </div>
      )}

      {showMySection && (
        <>
          <div className="border-t-2 border-dashed border-gray-300 dark:border-gray-700" />
          {ranking.myRank && user ? (
            <RankingEntryRow
              entry={{
                rank: ranking.myRank.rank,
                publicId: user.publicId,
                nickname: user.nickname,
                profileUrl: user.profileUrl,
                similarity: ranking.myRank.similarity,
                attemptCount: ranking.myRank.attemptCount,
              }}
              isPaused={isPaused}
            />
          ) : (
            <p className="text-sm font-bold text-gray-500 dark:text-gray-400 text-center py-2">
              추측해서 랭킹에 참여하세요
            </p>
          )}
        </>
      )}

      <div className="border-t-2 border-black dark:border-white pt-2 space-y-2">
        <div className="flex items-center justify-between text-xs font-bold text-gray-600 dark:text-gray-400">
          <span>
            <span
              className="inline-block w-1.5 h-1.5 bg-green-500 dark:bg-green-400 mr-1 align-middle"
              style={{ animation: "live-pulse 2s ease-in-out infinite" }}
            />
            <span className="text-black dark:text-white">오늘</span>{" "}
            <span className="text-black dark:text-white tabular-nums">{ranking?.totalPlayers ?? 0}</span>명 도전
          </span>
          {sseConnectionCount != null && (
            <span>
              현재 <span className="text-black dark:text-white tabular-nums">{sseConnectionCount}</span>명 접속 중
            </span>
          )}
        </div>

        {ranking?.yesterdayTotalPlayers != null && (
          <>
            <div className="border-t border-gray-300 dark:border-gray-700" />
            <p className="text-xs font-bold text-gray-600 dark:text-gray-400">
              <span className="text-black dark:text-white">어제</span>{" "}
              {isAuthenticated && ranking.yesterdayRank != null ? (
                <>
                  <span className="text-black dark:text-white tabular-nums">{ranking.yesterdayTotalPlayers}</span>명 중{" "}
                  <span className="text-indigo-600 dark:text-indigo-400 tabular-nums">{ranking.yesterdayRank}</span>등
                </>
              ) : (
                <>
                  <span className="text-black dark:text-white tabular-nums">{ranking.yesterdayTotalPlayers}</span>명
                  도전
                </>
              )}
            </p>
          </>
        )}
      </div>
    </Card>
  );
}
