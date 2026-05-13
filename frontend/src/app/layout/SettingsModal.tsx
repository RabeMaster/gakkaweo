import { useEffect, useState } from "react";
import { Dialog } from "@/shared/ui/Dialog";
import { useThemeStore } from "@/shared/stores/useThemeStore";
import {
  SOUND_VOLUME_KEY,
  type SoundType,
  getLastVolume,
  getSoundVolume,
  playSound,
  setCurrentSoundVolume,
  setLastVolume,
  stopCurrentSound,
} from "@/shared/config/sound";

type Theme = "light" | "dark" | "system";

const THEME_OPTIONS: { value: Theme; label: string; icon: string }[] = [
  { value: "light", label: "라이트", icon: "☀" },
  { value: "dark", label: "다크", icon: "☾" },
  { value: "system", label: "시스템", icon: "⚙" },
];

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const SOUND_TESTS: { label: string; type: SoundType; color: string }[] = [
  { label: "클리어", type: "clear", color: "bg-green-400" },
  { label: "실패", type: "fail", color: "bg-red-400" },
];

export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const { theme, setTheme } = useThemeStore();
  const [volume, setVolume] = useState(getSoundVolume);

  function handleVolumeChange(v: number) {
    setVolume(v);
    try {
      localStorage.setItem(SOUND_VOLUME_KEY, String(v));
    } catch {
      // 스토리지 접근 불가 시 무시
    }
    if (v > 0) {
      setLastVolume(v);
      setCurrentSoundVolume(v);
    } else {
      stopCurrentSound();
    }
  }

  function handleMuteToggle() {
    if (volume > 0) {
      handleVolumeChange(0);
    } else {
      handleVolumeChange(getLastVolume());
    }
  }

  function playTestSound(type: SoundType) {
    playSound(type, { stopPrevious: true });
  }

  useEffect(() => {
    if (!isOpen) {
      stopCurrentSound();
    }
  }, [isOpen]);

  return (
    <Dialog isOpen={isOpen} onClose={onClose} title="설정">
      <div className="space-y-5">
        <div>
          <h3 className="text-sm font-extrabold uppercase tracking-wider mb-3 text-gray-600 dark:text-gray-400">
            테마
          </h3>
          <div className="flex gap-2">
            {THEME_OPTIONS.map(({ value, label, icon }) => (
              <button
                type="button"
                key={value}
                onClick={() => setTheme(value)}
                className={[
                  "flex-1 border-4 border-black dark:border-white px-3 py-2.5 font-bold text-sm transition-all duration-100",
                  theme === value
                    ? "bg-black text-white dark:bg-white dark:text-black shadow-none translate-x-0 translate-y-0"
                    : "bg-white dark:bg-gray-900 text-black dark:text-white shadow-brutal-sm hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]",
                ].join(" ")}
              >
                <span className="block text-lg" aria-hidden="true">
                  {icon}
                </span>
                <span className="block mt-1">{label}</span>
              </button>
            ))}
          </div>
        </div>

        <div>
          <h3 className="text-sm font-extrabold uppercase tracking-wider mb-3 text-gray-600 dark:text-gray-400">
            사운드
          </h3>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handleMuteToggle}
              aria-label={volume === 0 ? "음소거 해제" : "음소거"}
              aria-pressed={volume === 0}
              className="relative border-2 border-black dark:border-white bg-white dark:bg-gray-900 w-7 h-7 shrink-0 flex items-center justify-center text-sm transition-all duration-100 shadow-brutal-sm-hover hover:shadow-none hover:translate-x-[1px] hover:translate-y-[1px] active:shadow-none active:translate-x-[1px] active:translate-y-[1px]"
            >
              {volume === 0 ? "🔇" : "🔊"}
              <span className="absolute -inset-2" aria-hidden="true" />
            </button>
            <input
              type="range"
              aria-label="사운드 볼륨"
              min="0"
              max="1"
              step="0.1"
              value={volume}
              onChange={(e) => handleVolumeChange(parseFloat(e.target.value))}
              className="flex-1 accent-yellow-400"
            />
            <span className="text-sm font-bold tabular-nums w-10 text-right">{Math.round(volume * 100)}%</span>
          </div>
        </div>

        <div>
          <h3 className="text-sm font-extrabold uppercase tracking-wider mb-3 text-gray-600 dark:text-gray-400">
            사운드 테스트
          </h3>
          <div className="flex gap-2">
            {SOUND_TESTS.map(({ label, type, color }) => (
              <button
                type="button"
                key={type}
                onClick={() => playTestSound(type)}
                disabled={volume === 0}
                className={[
                  `border-2 border-black dark:border-white px-3 py-1.5 text-xs font-black transition-all duration-100 ${color} text-black`,
                  "shadow-brutal-sm hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]",
                  "disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:shadow-brutal-sm disabled:hover:translate-x-0 disabled:hover:translate-y-0 disabled:active:shadow-brutal-sm disabled:active:translate-x-0 disabled:active:translate-y-0",
                ].join(" ")}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </Dialog>
  );
}
