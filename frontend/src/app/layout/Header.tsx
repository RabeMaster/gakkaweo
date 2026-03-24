import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { SettingsModal } from "@/app/layout/SettingsModal";

export function Header() {
  const { user, isAuthenticated } = useAuthStore();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  return (
    <>
      <header className="border-b-4 border-black dark:border-white bg-white dark:bg-gray-950">
        <div className="max-w-6xl mx-auto flex items-center justify-between px-6 py-4">
          <Link to="/" className="text-2xl font-black text-black dark:text-white tracking-tight">
            가까워
          </Link>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsSettingsOpen(true)}
              className="border-4 border-black dark:border-white bg-white dark:bg-gray-950 text-black dark:text-white px-3 py-1.5 text-sm font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
              aria-label="설정"
            >
              설정
            </button>

            {isAuthenticated && user ? (
              <Link
                to="/mypage"
                className="border-4 border-black dark:border-white bg-yellow-300 px-4 py-1.5 text-sm font-bold text-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
              >
                {user.nickname}
              </Link>
            ) : (
              <Link
                to="/login"
                className="border-4 border-black dark:border-white bg-green-400 px-4 py-1.5 text-sm font-bold text-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5"
              >
                로그인
              </Link>
            )}
          </div>
        </div>
      </header>

      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </>
  );
}
