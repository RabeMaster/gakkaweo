import type { GameMode } from "../types";

interface LobbyFiltersProps {
  modeFilter: GameMode | "ALL";
  onModeChange: (mode: GameMode | "ALL") => void;
  waitingOnly: boolean;
  onWaitingOnlyChange: (value: boolean) => void;
}

const MODE_OPTIONS: { value: GameMode | "ALL"; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "SENTENCE", label: "문장" },
  { value: "WORD", label: "단어" },
];

export function LobbyFilters({ modeFilter, onModeChange, waitingOnly, onWaitingOnlyChange }: LobbyFiltersProps) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex border-4 border-black dark:border-white">
        {MODE_OPTIONS.map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => onModeChange(opt.value)}
            className={[
              "px-4 py-1.5 text-sm font-black transition-colors duration-100",
              modeFilter === opt.value
                ? "bg-yellow-300 text-black"
                : "bg-white dark:bg-gray-900 text-black dark:text-white",
            ].join(" ")}
          >
            {opt.label}
          </button>
        ))}
      </div>
      <label className="flex items-center gap-2 cursor-pointer select-none">
        <input
          type="checkbox"
          checked={waitingOnly}
          onChange={(e) => onWaitingOnlyChange(e.target.checked)}
          className="w-5 h-5 accent-yellow-400"
        />
        <span className="text-sm font-bold text-gray-600 dark:text-gray-400">대기중만</span>
      </label>
    </div>
  );
}
