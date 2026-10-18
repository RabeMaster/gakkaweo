import { expect, it } from "vitest";
import { parseRankingUpdate } from "./sseEvent";

it.each(["", "invalid", "null", "{}", '{"rankings":null,"totalPlayers":1}', '{"rankings":[{}],"totalPlayers":1}'])(
  "ignores malformed ranking stream updates",
  (raw) => {
    expect(parseRankingUpdate(raw)).toBeNull();
  },
);

it("accepts the documented ranking event shape", () => {
  const value = {
    rankings: [
      { publicId: "test-user", nickname: "테스트", profileUrl: null, rank: 1, attemptCount: 2, similarity: 95.5 },
    ],
    totalPlayers: 1,
  };
  expect(parseRankingUpdate(JSON.stringify(value))).toEqual(value);
});
