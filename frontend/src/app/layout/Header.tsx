import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { getLastProvider, PROVIDER_COLORS } from "@/shared/config/providers";
import { KakaoIcon, GoogleIcon, NaverIcon, GakkaweoIcon } from "@/shared/config/providerIcons";
import { SettingsModal } from "@/app/layout/SettingsModal";

export function Header() {
  const { user, isAuthenticated } = useAuthStore();
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const provider = getLastProvider();
  const providerColors = provider ? PROVIDER_COLORS[provider] : null;
  const ProviderIcon = provider
    ? { kakao: KakaoIcon, google: GoogleIcon, naver: NaverIcon, local: GakkaweoIcon }[provider]
    : null;

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
              className="border-4 border-black dark:border-white bg-white dark:bg-gray-950 text-black dark:text-white px-3 py-1.5 text-sm font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
              aria-label="설정"
            >
              설정
            </button>

            {isAuthenticated && user ? (
              <Link
                to="/mypage"
                className={[
                  "inline-flex items-center gap-1.5 border-4 border-black dark:border-white px-3 py-1.5 text-sm font-bold shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]",
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
              <Link
                to="/login"
                className="border-4 border-black dark:border-white bg-green-400 px-4 py-1.5 text-sm font-bold text-black shadow-brutal-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]"
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
