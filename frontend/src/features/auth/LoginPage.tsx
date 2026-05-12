import { useState } from "react";
import { ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/Button";
import { Card } from "@/shared/ui/Card";
import { Input } from "@/shared/ui/Input";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { type Provider, PROVIDER_COLORS, getLastProvider, saveLastProvider } from "@/shared/config/providers";
import { KakaoIcon, GoogleIcon, NaverIcon } from "@/shared/config/providerIcons";
import { queryClient } from "@/shared/api/queryClient";
import { API_BASE_URL } from "@/shared/config/env";
import { login } from "@/features/auth/api";
import { RegisterDialog } from "@/features/auth/components/RegisterDialog";

const PROVIDERS: {
  id: Provider;
  loginLabel: string;
  icon: typeof KakaoIcon;
}[] = [
  { id: "kakao", loginLabel: "카카오 로그인", icon: KakaoIcon },
  { id: "google", loginLabel: "Google 로그인", icon: GoogleIcon },
  { id: "naver", loginLabel: "네이버 로그인", icon: NaverIcon },
];

function handleOAuthLogin(provider: Provider) {
  saveLastProvider(provider);
  window.location.href = `${API_BASE_URL}/oauth2/authorization/${provider}`;
}

export function LoginPage() {
  const lastProvider = getLastProvider();
  const { fetchUser } = useAuthStore();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);

  const canSubmit = !isSubmitting && username.length > 0 && password.length > 0;

  const handleLogin = async () => {
    if (!canSubmit) {
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await login(username, password);
      saveLastProvider("local");
      await fetchUser();
      queryClient.invalidateQueries({ queryKey: ["ranking"] });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("로그인에 실패했습니다");
      }
      setIsSubmitting(false);
    }
  };

  const handleRegisterSuccess = async () => {
    setIsRegisterOpen(false);
    saveLastProvider("local");
    await fetchUser();
    queryClient.invalidateQueries({ queryKey: ["ranking"] });
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl md:text-4xl font-black">로그인</h1>
        <p className="text-sm text-gray-600 dark:text-gray-400 font-medium">소셜 또는 가까워 계정으로 시작하세요</p>
      </div>

      <div className="flex flex-col md:flex-row gap-6 md:items-stretch">
        <Card className="flex-1 space-y-4">
          <h2 className="text-lg font-extrabold">소셜 로그인</h2>
          <p
            className={[
              "text-sm font-bold text-gray-600 dark:text-gray-400",
              lastProvider && lastProvider !== "local" ? "" : "invisible",
            ].join(" ")}
          >
            최근에{" "}
            <span className="text-black dark:text-white">
              {lastProvider && lastProvider !== "local" ? PROVIDER_COLORS[lastProvider].label : ""}
            </span>
            (으)로 로그인했습니다
          </p>
          <div className="space-y-3">
            {PROVIDERS.map(({ id, loginLabel, icon: Icon }) => {
              const { bg, text } = PROVIDER_COLORS[id];
              return (
                <button
                  key={id}
                  type="button"
                  onClick={() => handleOAuthLogin(id)}
                  className={[
                    "w-full border-4 border-black dark:border-white rounded-none font-bold text-base px-4 py-3",
                    "shadow-brutal transition-all duration-100",
                    "hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1",
                    "active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
                    bg,
                    text,
                  ].join(" ")}
                >
                  <span className="relative flex items-center justify-center">
                    <span className="absolute left-0 w-5 flex items-center justify-center">
                      <Icon />
                    </span>
                    <span>{loginLabel}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </Card>

        <div className="hidden md:flex flex-col items-center gap-4">
          <div className="flex-1 border-l-2 border-gray-300 dark:border-gray-700" />
          <span className="text-sm font-bold text-gray-400">또는</span>
          <div className="flex-1 border-l-2 border-gray-300 dark:border-gray-700" />
        </div>

        <div className="flex md:hidden items-center gap-4">
          <div className="flex-1 border-t-2 border-gray-300 dark:border-gray-700" />
          <span className="text-sm font-bold text-gray-400">또는</span>
          <div className="flex-1 border-t-2 border-gray-300 dark:border-gray-700" />
        </div>

        <Card className="flex-1 space-y-4">
          <h2 className="text-lg font-extrabold">가까워 로그인</h2>
          <p
            className={[
              "text-sm font-bold text-gray-600 dark:text-gray-400",
              lastProvider === "local" ? "" : "invisible",
            ].join(" ")}
          >
            최근에 <span className="text-black dark:text-white">{PROVIDER_COLORS.local.label}</span>(으)로
            로그인했습니다
          </p>
          <div className="space-y-3">
            <Input
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setError(null);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) {
                  handleLogin();
                }
              }}
              placeholder="아이디"
              disabled={isSubmitting}
              autoComplete="username"
            />
            <Input
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                setError(null);
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" && canSubmit) {
                  handleLogin();
                }
              }}
              placeholder="비밀번호"
              disabled={isSubmitting}
              autoComplete="current-password"
            />
          </div>

          {error && <p className="text-sm font-medium text-red-500">{error}</p>}

          <Button className="w-full" onClick={handleLogin} isLoading={isSubmitting} disabled={!canSubmit}>
            로그인
          </Button>

          <p className="text-sm font-medium text-center text-gray-600 dark:text-gray-400">
            계정이 없으신가요?{" "}
            <button
              type="button"
              onClick={() => setIsRegisterOpen(true)}
              className="font-bold text-black dark:text-white underline underline-offset-2 cursor-pointer hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
            >
              회원가입
            </button>
          </p>
        </Card>
      </div>

      <p className="text-xs text-gray-400 font-medium text-center">
        로그인 시 게임 기록이 저장되고 랭킹에 참여할 수 있습니다
      </p>

      {isRegisterOpen && <RegisterDialog onClose={() => setIsRegisterOpen(false)} onSuccess={handleRegisterSuccess} />}
    </div>
  );
}
