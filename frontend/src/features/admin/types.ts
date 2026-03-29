// --- Sentence ---

export interface SentenceResponse {
  publicId: string;
  sentence: string;
  status: "ACTIVE" | "DISABLED";
  usedAt: string | null;
  scheduledAt: string | null;
  totalPlayers: number | null;
  createdAt: string;
}

export interface SentenceListResponse {
  sentences: SentenceResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SentenceStatsResponse {
  totalSessions: number;
  clearedSessions: number;
  clearRate: number;
  avgSimilarity: number;
  avgAttemptCount: number;
}

export interface CsvUploadResponse {
  totalRows: number;
  successCount: number;
  duplicateCount: number;
}

export interface SimilarityTestResponse {
  sentence: string;
  guessText: string;
  similarity: number;
}

export interface DuplicateCheckResponse {
  hasDuplicate: boolean;
  similarEntries: { sentence: string; similarity: number }[];
}

// --- User ---

export interface AdminUserResponse {
  publicId: string;
  nickname: string;
  profileUrl: string | null;
  role: string;
  banned: boolean;
  bannedAt: string | null;
  provider: string;
  email: string | null;
  createdAt: string;
}

export interface UserListResponse {
  users: AdminUserResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ActivitySummary {
  totalParticipations: number;
  totalClears: number;
  avgAttemptCount: number;
  bestRank: number | null;
}

export interface UserDetailResponse extends AdminUserResponse {
  activity: ActivitySummary;
}

export interface GameHistoryEntry {
  date: string;
  sentence: string;
  gameStatus: string;
  bestSimilarity: number;
  attemptCount: number;
  finalRank: number | null;
  clearedAt: string | null;
}

export interface UserGameHistoryResponse {
  history: GameHistoryEntry[];
}

// --- Dashboard ---

export interface TodayWidgetResponse {
  sentenceId: string;
  sentence: string;
  totalParticipants: number;
  clearedCount: number;
  inProgressCount: number;
  avgSimilarity: number;
  avgAttemptCount: number;
  unusedSentenceCount: number;
  sseConnectionCount: number;
}

export interface FullRankingEntry {
  rank: number;
  publicId: string;
  nickname: string;
  profileUrl: string | null;
  similarity: number;
  attemptCount: number;
}

export interface FullRankingResponse {
  rankings: FullRankingEntry[];
  totalPlayers: number;
}

export interface DateStatsResponse {
  date: string;
  sentence: string;
  totalParticipants: number;
  clearedCount: number;
  clearRate: number;
  avgSimilarity: number;
  avgAttemptCount: number;
}

export interface DailyTrend {
  date: string;
  participants: number;
  clears: number;
  clearRate: number;
  newMembers: number;
}

export interface TrendDataResponse {
  trends: DailyTrend[];
}

export interface GuessLogEntry {
  memberPublicId: string | null;
  nickname: string;
  guessText: string;
  similarity: number;
  attemptNumber: number;
  createdAt: string;
}

export interface GuessLogResponse {
  logs: GuessLogEntry[];
}

// --- System ---

export interface AnnouncementResponse {
  id: number;
  adminNickname: string;
  title: string;
  content: string | null;
  type: "INFO" | "MAINTENANCE" | "WARNING";
  active: boolean;
  startsAt: string;
  endsAt: string | null;
  createdAt: string;
}

export interface SystemStatusResponse {
  sseConnectionCount: number;
  aiServiceHealthy: boolean;
  aiServiceResponseMs: number;
  redisHealthy: boolean;
  totalMembers: number;
  totalSentences: number;
  unusedSentences: number;
}

export interface AuditLog {
  id: number;
  adminNickname: string;
  action: string;
  targetType: string;
  targetId: string | null;
  detail: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface AuditLogPage {
  content: AuditLog[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
