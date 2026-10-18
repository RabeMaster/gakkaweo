export interface AnonymousGuess {
  guessText: string;
  similarity: number;
  attemptNumber: number;
  timestamp: string;
}

export interface AnonymousGameState {
  guesses: AnonymousGuess[];
  attemptCount: number;
  bestSimilarity: number;
  isCleared: boolean;
}

export const EMPTY_ANONYMOUS_STATE: AnonymousGameState = {
  guesses: [],
  attemptCount: 0,
  bestSimilarity: 0,
  isCleared: false,
};

export function parseAnonymousState(raw: string | null): AnonymousGameState {
  try {
    if (raw && raw.length > 8_000_000) {
      return { ...EMPTY_ANONYMOUS_STATE };
    }
    const value = raw ? JSON.parse(raw) : null;
    if (
      !value ||
      !Array.isArray(value.guesses) ||
      value.guesses.length > 10_000 ||
      typeof value.isCleared !== "boolean" ||
      !Number.isInteger(value.attemptCount) ||
      value.attemptCount < 0 ||
      value.attemptCount > value.guesses.length ||
      !value.guesses.every((guess: unknown) => {
        if (!guess || typeof guess !== "object") {
          return false;
        }
        const item = guess as AnonymousGuess;
        return (
          typeof item.guessText === "string" &&
          item.guessText.length > 0 &&
          item.guessText.length <= 500 &&
          typeof item.similarity === "number" &&
          Number.isFinite(item.similarity) &&
          item.similarity >= 0 &&
          item.similarity <= 100 &&
          Number.isInteger(item.attemptNumber) &&
          item.attemptNumber > 0 &&
          typeof item.timestamp === "string" &&
          item.timestamp.length <= 64 &&
          Number.isFinite(Date.parse(item.timestamp))
        );
      })
    ) {
      return { ...EMPTY_ANONYMOUS_STATE };
    }
    const guesses: AnonymousGuess[] = value.guesses;
    return {
      guesses,
      attemptCount: value.attemptCount,
      bestSimilarity: Math.max(0, ...guesses.map((guess) => guess.similarity)),
      isCleared: value.isCleared,
    };
  } catch {
    return { ...EMPTY_ANONYMOUS_STATE };
  }
}
