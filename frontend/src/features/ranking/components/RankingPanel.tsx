import type { RankingResponse } from "@/shared/api/types";
import { Card } from "@/shared/ui/Card";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { RankingEntryRow } from "@/features/ranking/components/RankingEntryRow";
import { RankingEmptyState } from "@/features/ranking/components/RankingEmptyState";

interface RankingPanelProps {
  ranking: RankingResponse | undefined;
  isLoading: boolean;
}

function SkeletonRow() {
  return <div className="h-10 bg-gray-200 dark:bg-gray-800 animate-pulse border-2 border-transparent" />;
}

export function RankingPanel({ ranking, isLoading }: RankingPanelProps) {
  const { user, isAuthenticated } = useAuthStore();

  const isInTop10 = ranking?.rankings.some((r) => r.publicId === user?.publicId) ?? false;
  const showMyRank = isAuthenticated && ranking?.myRank && !isInTop10;

  return (
    <Card className="w-72 shrink-0 self-start sticky top-24 !p-4 space-y-3">
      <h2 className="text-lg font-extrabold">실시간 랭킹</h2>

      <div className="space-y-1.5">
        {isLoading ? (
          <>
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
            <SkeletonRow />
          </>
        ) : !ranking || ranking.rankings.length === 0 ? (
          <RankingEmptyState />
        ) : (
          ranking.rankings.map((entry) => (
            <RankingEntryRow key={entry.publicId} entry={entry} isMe={entry.publicId === user?.publicId} />
          ))
        )}
      </div>

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
