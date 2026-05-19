import type { RoomSettings } from "../types";

interface RoomSettingsPanelProps {
  settings: RoomSettings;
  isHost: boolean;
  onEdit: () => void;
}

const MODE_LABELS: Record<string, string> = {
  SENTENCE: "문장",
  WORD: "단어",
};

export function RoomSettingsPanel({ settings, isHost, onEdit }: RoomSettingsPanelProps) {
  return (
    <div className="border-4 border-black dark:border-white shadow-brutal-sm bg-white dark:bg-gray-900 p-4 space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-black">방 설정</h3>
        {isHost && (
          <button
            type="button"
            onClick={onEdit}
            className="text-xs font-black text-indigo-600 dark:text-indigo-400 hover:underline"
          >
            편집
          </button>
        )}
      </div>
      <div className="grid grid-cols-2 gap-2 text-sm">
        <div>
          <span className="text-gray-500 dark:text-gray-400">모드</span>
          <p className="font-bold">{MODE_LABELS[settings.mode] ?? settings.mode}</p>
        </div>
        <div>
          <span className="text-gray-500 dark:text-gray-400">라운드</span>
          <p className="font-bold">{settings.rounds}</p>
        </div>
        <div>
          <span className="text-gray-500 dark:text-gray-400">제한 시간</span>
          <p className="font-bold">{settings.timeLimit}초</p>
        </div>
        <div>
          <span className="text-gray-500 dark:text-gray-400">최대 인원</span>
          <p className="font-bold">{settings.maxPlayers}명</p>
        </div>
      </div>
      {settings.guessPublic && <p className="text-xs font-bold text-indigo-600 dark:text-indigo-400">추측 공개</p>}
    </div>
  );
}
