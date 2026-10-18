import { expect, it } from "vitest";
import { parseAnonymousState } from "./anonymousState";
import { isCurrentGuessContext } from "./guessContext";

it.each([null, "not json", "null", "[]", '{"guesses":[{}]}', '{"guesses":"not an array"}'])(
  "ignores corrupt anonymous local history",
  (raw) => {
    expect(parseAnonymousState(raw)).toEqual({ guesses: [], attemptCount: 0, bestSimilarity: 0, isCleared: false });
  },
);

it("keeps server-confirmed clearance below 100 and derives the best score from valid guesses", () => {
  const guesses = [{ guessText: "유효한 추측", similarity: 95.5, attemptNumber: 1, timestamp: "2026-10-04T00:00:00Z" }];
  expect(
    parseAnonymousState(JSON.stringify({ guesses, attemptCount: 1, bestSimilarity: 999, isCleared: true })),
  ).toEqual({ guesses, attemptCount: 1, bestSimilarity: 95.5, isCleared: true });
});

it("ignores a late guess response after the puzzle or account changes", () => {
  const submitted = { sentenceId: "same", userId: "user-a", generation: 1 };
  expect(isCurrentGuessContext(submitted, { ...submitted, sentenceId: "new" })).toBe(false);
  expect(isCurrentGuessContext(submitted, { ...submitted, userId: "user-b" })).toBe(false);
  expect(isCurrentGuessContext(submitted, { ...submitted, userId: null })).toBe(false);
  expect(isCurrentGuessContext(submitted, { ...submitted, generation: 2 })).toBe(false);
  expect(isCurrentGuessContext(submitted, submitted)).toBe(true);
});
