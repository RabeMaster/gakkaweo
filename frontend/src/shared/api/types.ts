// --- 인증 ---

export interface MeResponse {
  publicId: string;
  nickname: string;
  profileUrl: string | null;
  role: "USER" | "ADMIN" | "SUPERADMIN";
}

// --- 일일 게임 ---

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

export interface HintEntry {
  guessText: string;
  similarity: number;
}

export interface HintResponse {
  hints: HintEntry[];
}

export interface YesterdayMeResponse {
  yesterdayDate: string | null;
  participated: boolean;
  rank: number | null;
  totalPlayers: number | null;
  bestGuessText: string | null;
  bestSimilarity: number | null;
  attemptCount: number | null;
  cleared: boolean | null;
}

// --- 랭킹 ---

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
}

// --- 공개 공지 ---

export interface ActiveAnnouncementResponse {
  id: number;
  title: string;
  content: string | null;
  type: "INFO" | "MAINTENANCE" | "WARNING";
  startsAt: string;
  endsAt: string | null;
}

// --- 오류 ---

export interface ErrorBody {
  status: number;
  code: string;
  message: string;
  timestamp: string;
}
