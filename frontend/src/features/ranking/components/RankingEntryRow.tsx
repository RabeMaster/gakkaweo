import { useState } from "react";
import type { RankingEntry } from "@/shared/api/types";
import { getSimilarityColor } from "@/shared/utils/similarity";
import { resolveProfileUrl } from "@/shared/utils/url";

interface RankingEntryRowProps {
  entry: RankingEntry;
}

const RANK_BADGE_COLORS: Record<number, string> = {
  1: "bg-yellow-400 dark:bg-yellow-300 text-black",
  2: "bg-slate-300 dark:bg-slate-400 text-black",
  3: "bg-amber-600 dark:bg-amber-500 text-white",
};

const TOP_RANK_STYLES: Record<number, { bg: string; shadowVar: string }> = {
  1: { bg: "bg-yellow-50 dark:bg-yellow-900/25", shadowVar: "--rank-1-accent" },
  2: { bg: "bg-slate-100 dark:bg-slate-800/40", shadowVar: "--rank-2-accent" },
  3: { bg: "bg-amber-50 dark:bg-amber-900/25", shadowVar: "--rank-3-accent" },
};

function TrophyIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" className="text-black">
      <path d="M5 3h14v7c0 3.9-3.1 7-7 7s-7-3.1-7-7V3z" />
      <path d="M5 5H2.5c0 3.5 1.5 5.5 2.5 6" />
      <path d="M19 5h2.5c0 3.5-1.5 5.5-2.5 6" />
      <rect x="10" y="17" width="4" height="2" />
      <rect x="7" y="19" width="10" height="2.5" />
    </svg>
  );
}

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
  const badgeColor = RANK_BADGE_COLORS[entry.rank] ?? "bg-gray-200 dark:bg-gray-700 text-black dark:text-white";
  const topStyle = TOP_RANK_STYLES[entry.rank];
  const isFirst = entry.rank === 1;
  const { bg } = getSimilarityColor(entry.similarity);

  return (
    <div
      className={[
        "px-2.5 py-1.5 border-2 border-black dark:border-white transition-all duration-100 space-y-1",
        topStyle ? topStyle.bg : "",
      ].join(" ")}
      style={
        isFirst
          ? { animation: "rank-shimmer 2.5s ease-in-out infinite" }
          : topStyle
            ? { boxShadow: `inset 4px 0 0 0 var(${topStyle.shadowVar})` }
            : undefined
      }
    >
      <div className="flex items-center gap-2">
        <span
          className={[
            "min-w-6 h-6 px-1 border-2 border-black dark:border-white flex items-center justify-center text-xs font-black shrink-0",
            badgeColor,
          ].join(" ")}
        >
          {isFirst ? <TrophyIcon /> : entry.rank}
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
