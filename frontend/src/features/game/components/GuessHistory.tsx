import { useState } from "react";
import { SimilarityBadge } from "@/shared/ui/SimilarityBadge";

interface GuessItem {
  guessText: string;
  similarity: number;
  attemptNumber: number | null;
}

interface GuessHistoryProps {
  guesses: GuessItem[];
}

const PAGE_SIZE = 5;

export function GuessHistory({ guesses }: GuessHistoryProps) {
  const [page, setPage] = useState(0);

  if (guesses.length === 0) {
    return null;
  }

  const sorted = [...guesses].reverse();
  const total = guesses.length;
  const totalPages = Math.ceil(sorted.length / PAGE_SIZE);
  const safePage = Math.min(page, totalPages - 1);
  const pageGuesses = sorted.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-extrabold">추측 기록</h2>
        {totalPages > 1 && (
          <span className="text-sm font-bold text-gray-500 dark:text-gray-400 tabular-nums">
            {safePage + 1} / {totalPages}
          </span>
        )}
      </div>

      <div className="space-y-3">
        {pageGuesses.map((guess, i) => (
          <div
            key={`${guess.attemptNumber ?? total - (safePage * PAGE_SIZE + i)}-${guess.guessText}`}
            className="flex items-center justify-between border-4 border-black dark:border-white shadow-brutal bg-white dark:bg-gray-900 p-3"
          >
            <div className="flex items-center gap-3">
              <span className="text-sm font-bold text-gray-500 dark:text-gray-400 tabular-nums shrink-0">
                #{guess.attemptNumber ?? total - (safePage * PAGE_SIZE + i)}
              </span>
              <span className="font-medium">{guess.guessText}</span>
            </div>
            <SimilarityBadge similarity={guess.similarity} />
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3">
          <button
            type="button"
            disabled={safePage === 0}
            onClick={() => setPage(safePage - 1)}
            className="border-2 border-black dark:border-white px-3 py-1 text-xs font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px] disabled:opacity-50 disabled:cursor-not-allowed disabled:translate-x-0 disabled:translate-y-0 disabled:hover:shadow-brutal-sm disabled:hover:translate-x-0 disabled:hover:translate-y-0 disabled:active:shadow-brutal-sm disabled:active:translate-x-0 disabled:active:translate-y-0 bg-white dark:bg-gray-900"
          >
            이전
          </button>
          <button
            type="button"
            disabled={safePage >= totalPages - 1}
            onClick={() => setPage(safePage + 1)}
            className="border-2 border-black dark:border-white px-3 py-1 text-xs font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px] disabled:opacity-50 disabled:cursor-not-allowed disabled:translate-x-0 disabled:translate-y-0 disabled:hover:shadow-brutal-sm disabled:hover:translate-x-0 disabled:hover:translate-y-0 disabled:active:shadow-brutal-sm disabled:active:translate-x-0 disabled:active:translate-y-0 bg-white dark:bg-gray-900"
          >
            다음
          </button>
        </div>
      )}
    </div>
  );
}
