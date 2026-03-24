import { SimilarityBadge } from "@/features/game/components/SimilarityBadge";

interface GuessItem {
  guessText: string;
  similarity: number;
  attemptNumber: number | null;
}

interface GuessHistoryProps {
  guesses: GuessItem[];
}

export function GuessHistory({ guesses }: GuessHistoryProps) {
  if (guesses.length === 0) {
    return null;
  }

  const sorted = [...guesses].reverse();
  const total = guesses.length;

  return (
    <div className="space-y-3">
      <h2 className="text-2xl font-extrabold">추측 기록</h2>
      <div className="space-y-3">
        {sorted.map((guess, i) => (
          <div
            key={`${guess.attemptNumber ?? total - i}-${guess.guessText}`}
            className="flex items-center justify-between border-4 border-black dark:border-white shadow-brutal bg-white dark:bg-gray-900 p-3"
          >
            <div className="flex items-center gap-3">
              <span className="text-sm font-bold text-gray-500 dark:text-gray-400 tabular-nums shrink-0">
                #{guess.attemptNumber ?? total - i}
              </span>
              <span className="font-medium">{guess.guessText}</span>
            </div>
            <SimilarityBadge similarity={guess.similarity} />
          </div>
        ))}
      </div>
    </div>
  );
}
