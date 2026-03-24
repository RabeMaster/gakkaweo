import { Card } from "@/shared/ui/Card";
import { type Provider, PROVIDER_COLORS, getLastProvider, saveLastProvider } from "@/shared/config/providers";
import { KakaoIcon, GoogleIcon, NaverIcon } from "@/shared/config/providerIcons";

const API_BASE = import.meta.env.VITE_API_BASE_URL;

const PROVIDERS: { id: Provider; loginLabel: string; icon: typeof KakaoIcon }[] = [
  { id: "kakao", loginLabel: "카카오 로그인", icon: KakaoIcon },
  { id: "google", loginLabel: "Google 로그인", icon: GoogleIcon },
  { id: "naver", loginLabel: "네이버 로그인", icon: NaverIcon },
];

function handleLogin(provider: Provider) {
  saveLastProvider(provider);
  window.location.href = `${API_BASE}/oauth2/authorization/${provider}`;
}

export function LoginPage() {
  const lastProvider = getLastProvider();

  return (
    <div className="max-w-md mx-auto space-y-6">
      <div className="space-y-2">
        <h1 className="text-4xl font-black">로그인</h1>
        <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">소셜 계정으로 간편하게 시작하세요</p>
      </div>

      <Card className="space-y-3">
        {lastProvider && (
          <p className="text-sm font-bold text-gray-600 dark:text-gray-400 pb-1">
            최근에 <span className="text-black dark:text-white">{PROVIDER_COLORS[lastProvider].label}</span>(으)로
            로그인했습니다
          </p>
        )}
        {PROVIDERS.map(({ id, loginLabel, icon: Icon }) => {
          const { bg, text } = PROVIDER_COLORS[id];
          return (
            <button
              key={id}
              type="button"
              onClick={() => handleLogin(id)}
              className={[
                "w-full border-4 border-black dark:border-white rounded-none font-bold text-base px-6 py-4",
                "shadow-brutal transition-all duration-100",
                "hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1",
                "active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
                bg,
                text,
              ].join(" ")}
            >
              <span className="inline-flex items-center gap-3 w-44">
                <span className="w-5 flex items-center justify-center shrink-0">
                  <Icon />
                </span>
                <span>{loginLabel}</span>
              </span>
            </button>
          );
        })}
      </Card>

      <p className="text-xs text-gray-400 font-medium text-center">
        로그인 시 게임 기록이 저장되고 랭킹에 참여할 수 있습니다
      </p>
    </div>
  );
}
