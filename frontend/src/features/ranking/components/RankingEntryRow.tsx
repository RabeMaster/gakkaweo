import { useState } from "react";
import type { RankingEntry } from "@/shared/api/types";
import { getSimilarityColor } from "@/shared/utils/similarity";
import { resolveProfileUrl } from "@/shared/utils/url";

interface RankingEntryRowProps {
  entry: RankingEntry;
}

const RANK_BADGE_COLORS: Record<number, string> = {
  1: "bg-yellow-400",
  2: "bg-gray-300",
  3: "bg-orange-300",
};

const RANK_ROW_COLORS: Record<number, string> = {
  1: "bg-yellow-50 dark:bg-yellow-900/20",
  2: "bg-gray-50 dark:bg-gray-800/30",
  3: "bg-orange-50 dark:bg-orange-900/20",
};

function DefaultAvatar() {
  return (
    <div className="w-7 h-7 border-2 border-black dark:border-white bg-gray-200 dark:bg-gray-800 flex items-center justify-center shrink-0">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" className="text-gray-400 dark:text-gray-500">
        <circle cx="12" cy="9" r="3.5" />
        <path d="M12 14c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
      </svg>
    </div>
  );
}

export function RankingEntryRow({ entry }: RankingEntryRowProps) {
  const [imgError, setImgError] = useState(false);
  const badgeColor = RANK_BADGE_COLORS[entry.rank] ?? "bg-white dark:bg-gray-900";
  const rowColor = RANK_ROW_COLORS[entry.rank] ?? "";
  const { bg } = getSimilarityColor(entry.similarity);

  return (
    <div
      className={[
        "px-2.5 py-1.5 border-2 border-black dark:border-white transition-all duration-100 space-y-1",
        rowColor,
      ].join(" ")}
    >
      <div className="flex items-center gap-2">
        <span
          className={[
            "min-w-6 h-6 px-1 border-2 border-black dark:border-white flex items-center justify-center text-xs font-black shrink-0",
            badgeColor,
          ].join(" ")}
        >
          {entry.rank}
        </span>

        {entry.profileUrl && !imgError ? (
          <img
            src={resolveProfileUrl(entry.profileUrl) ?? ""}
            alt=""
            className="w-7 h-7 border-2 border-black dark:border-white object-cover shrink-0"
            onError={() => setImgError(true)}
          />
        ) : (
          <DefaultAvatar />
        )}

        <span className="text-sm font-bold truncate min-w-0 flex-1">{entry.nickname}</span>
      </div>

      <div className="flex items-center gap-1.5">
        <div className="relative h-4 border-2 border-black dark:border-white bg-gray-100 dark:bg-gray-800 overflow-hidden flex-1">
          <div
            className="absolute inset-y-0 left-0"
            style={{ width: `${Math.min(100, entry.similarity)}%`, backgroundColor: bg }}
          />
          <span className="absolute inset-0 flex items-center justify-center text-[10px] font-black text-black dark:text-white tabular-nums">
            {entry.similarity.toFixed(1)}%
          </span>
        </div>
        <span className="text-[10px] font-bold text-gray-500 dark:text-gray-400 tabular-nums shrink-0">
          {entry.attemptCount}회
        </span>
      </div>
    </div>
  );
}
