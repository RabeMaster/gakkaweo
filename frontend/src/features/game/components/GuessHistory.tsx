import { useState } from "react";
import { Button } from "@/shared/ui/Button";
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

const ITEM_CLASS =
  "flex items-center justify-between border-4 border-black dark:border-white shadow-brutal bg-white dark:bg-gray-900 p-2 md:p-3";

const PLACEHOLDER_CLASS =
  "flex items-center justify-between border-4 border-transparent p-2 md:p-3 shadow-brutal invisible";

function PlaceholderRow() {
  return (
    <div className={PLACEHOLDER_CLASS} aria-hidden="true">
      <div className="flex items-center gap-3">
        <span className="text-sm font-bold tabular-nums shrink-0">#0</span>
        <span className="font-medium">&nbsp;</span>
      </div>
      <span className="inline-block border-2 border-transparent px-2 py-0.5 text-sm font-bold tabular-nums">0.0%</span>
    </div>
  );
}

export function GuessHistory({ guesses }: GuessHistoryProps) {
  const [page, setPage] = useState(0);

  const sorted = [...guesses].reverse();
  const total = guesses.length;
  const totalPages = Math.max(1, Math.ceil(sorted.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages - 1);
  const pageGuesses = sorted.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE);
  const placeholderCount = PAGE_SIZE - pageGuesses.length;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-xl md:text-2xl font-extrabold">추측 기록</h2>
        {totalPages > 1 && (
          <div className="flex items-center gap-2">
            <Button size="sm" variant="secondary" disabled={safePage === 0} onClick={() => setPage(safePage - 1)}>
              이전
            </Button>
            <span className="text-sm font-bold text-gray-500 dark:text-gray-400 tabular-nums">
              {safePage + 1} / {totalPages}
            </span>
            <Button
              size="sm"
              variant="secondary"
              disabled={safePage >= totalPages - 1}
              onClick={() => setPage(safePage + 1)}
            >
              다음
            </Button>
          </div>
        )}
      </div>

      <div className="space-y-3">
        {pageGuesses.map((guess, i) => (
          <div
            key={`${guess.attemptNumber ?? total - (safePage * PAGE_SIZE + i)}-${guess.guessText}`}
            className={ITEM_CLASS}
          >
            <div className="flex items-center gap-3">
              <span className="text-sm font-bold text-gray-500 dark:text-gray-400 tabular-nums shrink-0">
                #{guess.attemptNumber ?? total - (safePage * PAGE_SIZE + i)}
              </span>
              <span className="font-medium min-w-0 break-words">{guess.guessText}</span>
            </div>
            <SimilarityBadge similarity={guess.similarity} />
          </div>
        ))}
        {placeholderCount > 0 &&
          Array.from({ length: placeholderCount }, (_, i) => <PlaceholderRow key={`placeholder-${i}`} />)}
      </div>
    </div>
  );
}
