import { useState, useRef, useEffect } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { getLastProvider, PROVIDER_COLORS } from "@/shared/config/providers";
import { KakaoIcon, GoogleIcon, NaverIcon, GakkaweoIcon } from "@/shared/config/providerIcons";
import { hasAdminAccess } from "@/shared/utils/role";
import { SettingsModal } from "@/app/layout/SettingsModal";

const SM_BUTTON =
  "border-4 border-black dark:border-white px-3 py-1.5 text-sm font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]";

export function Header() {
  const { user, isAuthenticated } = useAuthStore();
  const location = useLocation();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [trackedPath, setTrackedPath] = useState(location.pathname);
  const menuRef = useRef<HTMLDivElement>(null);
  const isAdmin = hasAdminAccess(user?.role);
  const provider = getLastProvider();
  const providerColors = provider ? PROVIDER_COLORS[provider] : null;
  const ProviderIcon = provider
    ? { kakao: KakaoIcon, google: GoogleIcon, naver: NaverIcon, local: GakkaweoIcon }[provider]
    : null;

  if (location.pathname !== trackedPath) {
    setTrackedPath(location.pathname);
    setIsMenuOpen(false);
  }

  useEffect(() => {
    if (!isMenuOpen) {
      return;
    }

    function handleMouseDown(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setIsMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handleMouseDown);
    return () => {
      document.removeEventListener("mousedown", handleMouseDown);
    };
  }, [isMenuOpen]);

  return (
    <>
      <header className="relative border-b-4 border-black dark:border-white bg-white dark:bg-gray-950">
        <div className="max-w-6xl mx-auto flex items-center justify-between px-4 py-3 md:px-6 md:py-4">
          <Link
            to="/"
            className="flex items-center gap-2 text-2xl font-black text-black dark:text-white tracking-tight"
          >
            <GakkaweoIcon size={28} />
            가까워
          </Link>

          <div className="hidden md:flex items-center gap-3">
            <button
              type="button"
              onClick={() => setIsSettingsOpen(true)}
              className={`${SM_BUTTON} bg-white dark:bg-gray-950 text-black dark:text-white`}
              aria-label="설정"
            >
              설정
            </button>

            {isAdmin && (
              <Link
                to="/admin"
                className={[
                  SM_BUTTON,
                  location.pathname.startsWith("/admin") ? "bg-red-400 text-black" : "bg-red-200 text-black",
                ].join(" ")}
              >
                관리
              </Link>
            )}

            {isAuthenticated && user ? (
              <Link
                to="/mypage"
                className={[
                  `inline-flex items-center gap-1.5 ${SM_BUTTON}`,
                  providerColors ? `${providerColors.bg} ${providerColors.text}` : "bg-yellow-300 text-black",
                ].join(" ")}
              >
                {ProviderIcon && (
                  <span className="flex items-center justify-center shrink-0">
                    <ProviderIcon size={14} />
                  </span>
                )}
                {user.nickname}
              </Link>
            ) : (
              <Link to="/login" className={`${SM_BUTTON} bg-green-400 text-black`}>
                로그인
              </Link>
            )}
          </div>

          <div className="md:hidden" ref={menuRef}>
            <button
              type="button"
              onClick={() => setIsMenuOpen((prev) => !prev)}
              className={`${SM_BUTTON} bg-white dark:bg-gray-950 text-black dark:text-white`}
              aria-label={isMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
              aria-expanded={isMenuOpen}
            >
              <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                {isMenuOpen ? (
                  <path d="M5.293 5.293a1 1 0 011.414 0L10 8.586l3.293-3.293a1 1 0 111.414 1.414L11.414 10l3.293 3.293a1 1 0 01-1.414 1.414L10 11.414l-3.293 3.293a1 1 0 01-1.414-1.414L8.586 10 5.293 6.707a1 1 0 010-1.414z" />
                ) : (
                  <path d="M3 5h14a1 1 0 010 2H3a1 1 0 010-2zm0 4h14a1 1 0 010 2H3a1 1 0 010-2zm0 4h14a1 1 0 010 2H3a1 1 0 010-2z" />
                )}
              </svg>
            </button>

            {isMenuOpen && (
              <div className="absolute right-0 left-0 border-b-4 border-x-4 border-black dark:border-white bg-white dark:bg-gray-950 shadow-brutal z-40">
                <div className="flex flex-col p-3 gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setIsMenuOpen(false);
                      setIsSettingsOpen(true);
                    }}
                    className={`w-full ${SM_BUTTON} bg-white dark:bg-gray-950 text-black dark:text-white text-left`}
                  >
                    설정
                  </button>

                  {isAdmin && (
                    <Link
                      to="/admin"
                      className={[
                        `w-full block ${SM_BUTTON}`,
                        location.pathname.startsWith("/admin") ? "bg-red-400 text-black" : "bg-red-200 text-black",
                      ].join(" ")}
                    >
                      관리
                    </Link>
                  )}

                  {isAuthenticated && user ? (
                    <Link
                      to="/mypage"
                      className={[
                        `w-full inline-flex items-center gap-1.5 ${SM_BUTTON}`,
                        providerColors ? `${providerColors.bg} ${providerColors.text}` : "bg-yellow-300 text-black",
                      ].join(" ")}
                    >
                      {ProviderIcon && (
                        <span className="flex items-center justify-center shrink-0">
                          <ProviderIcon size={14} />
                        </span>
                      )}
                      {user.nickname}
                    </Link>
                  ) : (
                    <Link to="/login" className={`w-full block ${SM_BUTTON} bg-green-400 text-black`}>
                      로그인
                    </Link>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      </header>

      <SettingsModal isOpen={isSettingsOpen} onClose={() => setIsSettingsOpen(false)} />
    </>
  );
}
