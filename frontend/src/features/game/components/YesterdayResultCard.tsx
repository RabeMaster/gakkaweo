import { SimilarityBadge } from "@/shared/ui/SimilarityBadge";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useYesterdayMe } from "@/features/game/hooks/useGameQueries";

interface YesterdayResultCardProps {
  sentence: string;
  date: string;
}

export function YesterdayResultCard({ sentence, date }: YesterdayResultCardProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const { data: me } = useYesterdayMe();

  const showRecord = isAuthenticated && me?.participated === true;

  return (
    <div className="border-4 border-black dark:border-white shadow-brutal bg-indigo-50 dark:bg-gray-800 p-4">
      <p className="text-sm font-medium text-gray-600 dark:text-gray-400">어제의 결과 ({date})</p>
      <p className="text-lg font-extrabold mt-1 break-words">{sentence}</p>

      {!isAuthenticated && (
        <p className="mt-2 text-sm font-medium text-gray-600 dark:text-gray-400">
          로그인하면 어제의 내 기록을 볼 수 있어요
        </p>
      )}

      {showRecord && me && (
        <div className="mt-3 border-t-2 border-black/20 dark:border-white/20 pt-3 space-y-1.5">
          {me.cleared ? (
            <p className="text-base font-bold">🎉 정답을 맞혔어요!</p>
          ) : (
            me.bestGuessText != null &&
            me.bestSimilarity != null && (
              <div className="flex items-center justify-between gap-2">
                <p className="text-base font-bold break-words min-w-0">
                  <span className="text-sm text-gray-500 dark:text-gray-400">나의 최고 추측:</span> {me.bestGuessText}
                </p>
                <span className="shrink-0">
                  <SimilarityBadge similarity={me.bestSimilarity} />
                </span>
              </div>
            )
          )}
          {me.attemptCount != null && (
            <p className="text-sm font-bold text-gray-600 dark:text-gray-400 tabular-nums">
              {me.rank != null && me.totalPlayers != null
                ? `${me.totalPlayers}명 중 ${me.rank}등, ${me.attemptCount}번 시도`
                : `${me.attemptCount}번 시도`}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
