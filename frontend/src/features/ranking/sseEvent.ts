import type { RankingResponse } from "@/shared/api/types";

export function parseRankingUpdate(raw: string): Pick<RankingResponse, "rankings" | "totalPlayers"> | null {
  try {
    const value = JSON.parse(raw);
    if (
      !value ||
      !Array.isArray(value.rankings) ||
      !Number.isInteger(value.totalPlayers) ||
      value.totalPlayers < 0 ||
      !value.rankings.every((entry: unknown) => {
        if (!entry || typeof entry !== "object") {
          return false;
        }
        const item = entry as RankingResponse["rankings"][number];
        return (
          typeof item.publicId === "string" &&
          typeof item.nickname === "string" &&
          (item.profileUrl === null || typeof item.profileUrl === "string") &&
          Number.isInteger(item.rank) &&
          item.rank > 0 &&
          Number.isInteger(item.attemptCount) &&
          item.attemptCount >= 0 &&
          Number.isFinite(item.similarity) &&
          item.similarity >= 0 &&
          item.similarity <= 100
        );
      })
    ) {
      return null;
    }
    return { rankings: value.rankings, totalPlayers: value.totalPlayers };
  } catch {
    return null;
  }
}
