import { useEffect, useRef, useState } from "react";
import type { RankingResponse } from "@/shared/api/types";
import { Card } from "@/shared/ui/Card";
import { useAuthStore } from "@/shared/stores/useAuthStore";
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

  const entries = (ranking?.rankings ?? []).slice(0, 10);
  const shouldAnimate = entries.length > VISIBLE;

  const [offset, setOffset] = useState(0);
  const [isSliding, setIsSliding] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const [itemHeight, setItemHeight] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!listRef.current?.firstElementChild) {
      return;
    }
    setItemHeight((listRef.current.firstElementChild as HTMLElement).offsetHeight);
  }, [entries.length, isLoading]);

  useEffect(() => {
    if (!shouldAnimate || isPaused || itemHeight === 0) {
      return;
    }
    const id = setInterval(() => {
      setIsSliding(true);
    }, ROTATE_MS);
    return () => clearInterval(id);
  }, [shouldAnimate, isPaused, itemHeight]);

  const handleTransitionEnd = () => {
    setIsSliding(false);
    setOffset((o) => (o + 1) % entries.length);
  };

  const visibleEntries = shouldAnimate ? getCircularSlice(entries, offset, isSliding ? VISIBLE + 1 : VISIBLE) : entries;

  const stride = itemHeight + GAP_PX;
  const containerHeight = shouldAnimate && itemHeight > 0 ? VISIBLE * itemHeight + (VISIBLE - 1) * GAP_PX : undefined;

  const isInTop10 = ranking?.rankings.some((r) => r.publicId === user?.publicId) ?? false;
  const showMyRank = isAuthenticated && ranking?.myRank && !isInTop10;

  return (
    <Card className="w-72 shrink-0 self-start sticky top-24 !p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-extrabold">실시간 랭킹</h2>
        {shouldAnimate && (
          <button
            type="button"
            onClick={() => setIsPaused((p) => !p)}
            className="text-xs font-bold text-gray-500 dark:text-gray-400 hover:text-black dark:hover:text-white transition-colors"
            aria-label={isPaused ? "자동 순환 시작" : "자동 순환 정지"}
          >
            {isPaused ? "▶" : "❚❚"}
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
          onMouseEnter={() => setIsPaused(true)}
          onMouseLeave={() => setIsPaused(false)}
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
              <RankingEntryRow key={entry.publicId} entry={entry} isMe={entry.publicId === user?.publicId} />
            ))}
          </div>
        </div>
      )}

      {showMyRank && ranking?.myRank && user && (
        <>
          <div className="border-t-2 border-dashed border-gray-300 dark:border-gray-700" />
          <RankingEntryRow
            entry={{
              rank: ranking.myRank.rank,
              publicId: user.publicId,
              nickname: user.nickname,
              profileUrl: user.profileUrl,
              similarity: ranking.myRank.similarity,
              attemptCount: ranking.myRank.attemptCount,
            }}
            isMe
          />
        </>
      )}

      <div className="border-t-2 border-black dark:border-white pt-2 space-y-2">
        <p className="text-xs font-bold text-gray-600 dark:text-gray-400">
          <span className="text-black dark:text-white">오늘</span>{" "}
          <span className="text-black dark:text-white tabular-nums">{ranking?.totalPlayers ?? 0}</span>명 도전 중
        </p>

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
