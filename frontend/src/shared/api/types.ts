// --- Auth ---

export interface MeResponse {
  publicId: string;
  nickname: string;
  profileUrl: string | null;
  role: "ROLE_USER" | "ROLE_ADMIN";
}

// --- Daily Game ---

export interface TodayResponse {
  sentenceId: string;
  hintMask: string;
  wordCount: number;
  charCounts: number[];
  expiresAt: string;
  yesterdaySentence: string | null;
  yesterdayDate: string | null;
}

export interface GuessRequest {
  sentenceId: string;
  guessText: string;
}

export interface GuessResponse {
  similarity: number;
  attemptNumber: number | null;
  isCorrect: boolean;
  gameStatus: "IN_PROGRESS" | "CLEARED" | null;
  timestamp: string;
}

export interface GuessHistoryItem {
  guessText: string;
  similarity: number;
  attemptNumber: number;
  createdAt: string;
}

export interface HistoryResponse {
  guesses: GuessHistoryItem[];
}

export interface StatusResponse {
  sentenceId: string;
  gameStatus: "IN_PROGRESS" | "CLEARED" | "EXPIRED" | null;
  bestSimilarity: number;
  attemptCount: number;
  clearedAt: string | null;
}

// --- Ranking ---

export interface RankingEntry {
  rank: number;
  publicId: string;
  nickname: string;
  profileUrl: string | null;
  similarity: number;
  attemptCount: number;
}

export interface MyRank {
  rank: number;
  similarity: number;
  attemptCount: number;
}

export interface RankingResponse {
  rankings: RankingEntry[];
  totalPlayers: number;
  myRank: MyRank | null;
  yesterdayRank: number | null;
  yesterdayTotalPlayers: number | null;
}

// --- Error ---

export interface ErrorBody {
  status: number;
  code: string;
  message: string;
  timestamp: string;
}
