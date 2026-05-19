export type GameMode = "SENTENCE" | "WORD";

export type RoomStatus = "WAITING" | "COUNTDOWN" | "PLAYING" | "ROUND_RESULT" | "GAME_RESULT" | "ABANDONED";

export interface PlayerInfo {
  publicId: string;
  nickname: string;
  ready: boolean;
  isHost: boolean;
}

export interface RoomSettings {
  title: string;
  mode: GameMode;
  rounds: number;
  timeLimit: number;
  maxPlayers: number;
  password: string | null;
  guessPublic: boolean;
}

export interface RoomSnapshot {
  roomId: string;
  settings: RoomSettings;
  status: RoomStatus;
  hostPublicId: string;
  players: PlayerInfo[];
  createdAt: string;
}

export interface LobbyRoomInfo {
  roomId: string;
  title: string;
  mode: GameMode;
  playerCount: number;
  maxPlayers: number;
  status: RoomStatus;
  hasPassword: boolean;
  hostNickname: string;
}

export interface WsNotification {
  type: string;
  payload: unknown;
}

export interface WsError {
  type: string;
  reason: string;
  message: string;
}
