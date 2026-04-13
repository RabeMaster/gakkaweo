import { useEffect, useRef, useState } from "react";
import { useThemeStore } from "@/shared/stores/useThemeStore";
import { SOUND_VOLUME_KEY, getSoundVolume } from "@/shared/config/sound";

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

const SOUND_TESTS: { label: string; file: string; color: string }[] = [
  { label: "클리어", file: "/sounds/clear.mp3", color: "bg-green-400" },
];

export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const { theme, setTheme } = useThemeStore();
  const modalRef = useRef<HTMLDivElement>(null);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [volume, setVolume] = useState(getSoundVolume);

  function handleVolumeChange(v: number) {
    setVolume(v);
    try {
      localStorage.setItem(SOUND_VOLUME_KEY, String(v));
    } catch {
      // 스토리지 접근 불가 시 무시
    }
    if (audioRef.current) {
      audioRef.current.volume = v;
    }
  }

  function stopAudio() {
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.currentTime = 0;
      audioRef.current = null;
    }
  }

  function playTestSound(file: string) {
    stopAudio();
    const audio = new Audio(file);
    audio.volume = volume;
    audio.play().catch(() => {});
    audioRef.current = audio;
  }

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const handleClickOutside = (e: MouseEvent) => {
      if (modalRef.current && !modalRef.current.contains(e.target as Node)) {
        onClose();
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen, onClose]);

  useEffect(() => {
    if (!isOpen) {
      stopAudio();
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-labelledby="settings-modal-title"
    >
      <div
        ref={modalRef}
        className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-sm"
      >
        <div className="flex items-center justify-between border-b-4 border-black dark:border-white px-6 py-4">
          <h2 id="settings-modal-title" className="text-xl font-black">
            설정
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-3 py-1 font-black text-lg transition-all duration-100 shadow-brutal-sm hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
            aria-label="설정 닫기"
          >
            ✕
          </button>
        </div>

        <div className="px-6 py-5 space-y-5">
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
              <span className="text-lg shrink-0" aria-hidden="true">
                {volume === 0 ? "🔇" : "🔊"}
              </span>
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
              {SOUND_TESTS.map(({ label, file, color }) => (
                <button
                  type="button"
                  key={file}
                  onClick={() => playTestSound(file)}
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
      </div>
    </div>
  );
}
