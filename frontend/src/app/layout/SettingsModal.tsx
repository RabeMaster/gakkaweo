import { useEffect, useRef } from "react";
import { useThemeStore } from "@/shared/stores/useThemeStore";

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

export function SettingsModal({ isOpen, onClose }: SettingsModalProps) {
  const { theme, setTheme } = useThemeStore();
  const modalRef = useRef<HTMLDivElement>(null);

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

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div
        ref={modalRef}
        className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 shadow-brutal w-full max-w-sm"
      >
        <div className="flex items-center justify-between border-b-4 border-black dark:border-white px-6 py-4">
          <h2 className="text-xl font-black">설정</h2>
          <button
            onClick={onClose}
            className="border-4 border-black dark:border-white bg-white dark:bg-gray-900 text-black dark:text-white px-3 py-1 font-black text-lg transition-all duration-100 shadow-brutal-sm hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
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
                  key={value}
                  onClick={() => setTheme(value)}
                  className={[
                    "flex-1 border-4 border-black dark:border-white px-3 py-2.5 font-bold text-sm transition-all duration-100",
                    theme === value
                      ? "bg-black text-white dark:bg-white dark:text-black shadow-none translate-x-0 translate-y-0"
                      : "bg-white dark:bg-gray-900 text-black dark:text-white shadow-brutal-sm hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
                  ].join(" ")}
                >
                  <span className="block text-lg">{icon}</span>
                  <span className="block mt-1">{label}</span>
                </button>
              ))}
            </div>
          </div>

          <div>
            <h3 className="text-sm font-extrabold uppercase tracking-wider mb-3 text-gray-600 dark:text-gray-400">
              사운드
            </h3>
            <p className="text-sm text-gray-400 font-medium">추후 지원 예정</p>
          </div>
        </div>
      </div>
    </div>
  );
}
