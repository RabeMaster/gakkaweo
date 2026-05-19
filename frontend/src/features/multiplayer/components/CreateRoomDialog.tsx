import { useState } from "react";
import { Dialog } from "@/shared/ui/Dialog";
import { Input } from "@/shared/ui/Input";
import { Button } from "@/shared/ui/Button";
import type { GameMode } from "../types";

interface CreateRoomDialogProps {
  onClose: () => void;
  onCreate: (params: {
    title: string;
    mode: GameMode;
    rounds: number;
    timeLimit: number;
    maxPlayers: number;
    password: string | null;
    guessPublic: boolean;
  }) => void;
}

const ROUND_OPTIONS = [1, 3, 5, 7, 10];
const MAX_PLAYER_OPTIONS = [2, 3, 4, 5, 6];

const DEFAULT_TIME: Record<GameMode, number> = {
  SENTENCE: 120,
  WORD: 60,
};

export function CreateRoomDialog({ onClose, onCreate }: CreateRoomDialogProps) {
  const [title, setTitle] = useState("");
  const [mode, setMode] = useState<GameMode>("SENTENCE");
  const [rounds, setRounds] = useState(5);
  const [timeLimit, setTimeLimit] = useState(120);
  const [maxPlayers, setMaxPlayers] = useState(6);
  const [password, setPassword] = useState("");
  const [guessPublic, setGuessPublic] = useState(false);

  const handleModeChange = (newMode: GameMode) => {
    setMode(newMode);
    setTimeLimit(DEFAULT_TIME[newMode]);
  };

  const handleSubmit = () => {
    if (!title.trim() || title.length > 30) {
      return;
    }
    onCreate({
      title: title.trim(),
      mode,
      rounds,
      timeLimit,
      maxPlayers,
      password: password || null,
      guessPublic,
    });
  };

  return (
    <Dialog
      onClose={onClose}
      title="방 만들기"
      maxWidth="max-w-md"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            취소
          </Button>
          <Button size="sm" onClick={handleSubmit} disabled={!title.trim()}>
            만들기
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="block text-sm font-black mb-1">방 제목</label>
          <Input
            size="sm"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="방 제목 (1~30자)"
            maxLength={30}
          />
        </div>

        <div>
          <label className="block text-sm font-black mb-1">모드</label>
          <div className="flex border-4 border-black dark:border-white">
            {(["SENTENCE", "WORD"] as GameMode[]).map((m) => (
              <button
                key={m}
                type="button"
                onClick={() => handleModeChange(m)}
                className={[
                  "flex-1 px-4 py-2 text-sm font-black transition-colors",
                  mode === m ? "bg-yellow-300 text-black" : "bg-white dark:bg-gray-900 text-black dark:text-white",
                ].join(" ")}
              >
                {m === "SENTENCE" ? "문장" : "단어"}
              </button>
            ))}
          </div>
        </div>

        <div className="flex gap-4">
          <div className="flex-1">
            <label className="block text-sm font-black mb-1">라운드</label>
            <select
              value={rounds}
              onChange={(e) => setRounds(Number(e.target.value))}
              className="w-full border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white shadow-brutal-sm px-3 py-1.5 text-sm font-bold dark:[color-scheme:dark]"
            >
              {ROUND_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {r}라운드
                </option>
              ))}
            </select>
          </div>
          <div className="flex-1">
            <label className="block text-sm font-black mb-1">제한 시간</label>
            <select
              value={timeLimit}
              onChange={(e) => setTimeLimit(Number(e.target.value))}
              className="w-full border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white shadow-brutal-sm px-3 py-1.5 text-sm font-bold dark:[color-scheme:dark]"
            >
              {(mode === "SENTENCE" ? [60, 90, 120, 150, 180] : [30, 45, 60, 90, 120]).map((t) => (
                <option key={t} value={t}>
                  {t}초
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-sm font-black mb-1">최대 인원</label>
          <div className="flex border-4 border-black dark:border-white">
            {MAX_PLAYER_OPTIONS.map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setMaxPlayers(n)}
                className={[
                  "flex-1 px-3 py-2 text-sm font-black transition-colors",
                  maxPlayers === n
                    ? "bg-yellow-300 text-black"
                    : "bg-white dark:bg-gray-900 text-black dark:text-white",
                ].join(" ")}
              >
                {n}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label className="block text-sm font-black mb-1">비밀번호 (선택)</label>
          <Input
            size="sm"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비어두면 공개"
            maxLength={16}
          />
        </div>

        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={guessPublic}
            onChange={(e) => setGuessPublic(e.target.checked)}
            className="w-5 h-5 accent-yellow-400"
          />
          <span className="text-sm font-bold">추측 공개</span>
        </label>
      </div>
    </Dialog>
  );
}
