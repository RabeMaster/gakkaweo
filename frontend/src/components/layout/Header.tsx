import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { useAuthStore } from "@/stores/useAuthStore";
import { SettingsModal } from "@/components/layout/SettingsModal";

const NAV_ITEMS = [
  { to: "/", label: "게임" },
  { to: "/ranking", label: "랭킹" },
] as const;

export function Header() {
  const { user, isAuthenticated } = useAuthStore();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);

  return (
    <>
      <header className="border-b-4 border-black dark:border-white bg-white dark:bg-gray-950">
        <div className="max-w-5xl mx-auto flex items-center justify-between px-6 py-4">
          <Link to="/" className="text-2xl font-black text-black dark:text-white tracking-tight">
            가까워
          </Link>

          <nav className="flex items-center gap-2">
            {NAV_ITEMS.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === "/"}
                className={({ isActive }) =>
                  [
                    "border-4 border-black dark:border-white px-4 py-1.5 font-bold text-sm transition-all duration-100",
                    isActive
                      ? "bg-black text-white dark:bg-white dark:text-black shadow-none translate-x-0 translate-y-0"
                      : "bg-white dark:bg-gray-950 text-black dark:text-white shadow-brutal-sm hover:shadow-brutal-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-0.75 active:translate-y-0.75",
                  ].join(" ")
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsSettingsOpen(true)}
              className="border-4 border-black dark:border-white bg-white dark:bg-gray-950 text-black dark:text-white px-3 py-1.5 text-sm font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-0.75 active:translate-y-0.75"
              aria-label="설정"
            >
              설정
            </button>

            {isAuthenticated && user ? (
              <Link
                to="/mypage"
                className="border-4 border-black dark:border-white bg-yellow-300 px-4 py-1.5 text-sm font-bold text-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-0.75 active:translate-y-0.75"
              >
                {user.nickname}
              </Link>
            ) : (
              <Link
                to="/login"
                className="border-4 border-black dark:border-white bg-green-400 px-4 py-1.5 text-sm font-bold text-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-0.75 active:translate-y-0.75"
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
