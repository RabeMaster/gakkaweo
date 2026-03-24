export type Provider = "kakao" | "google" | "naver" | "local";

const STORAGE_KEY = "gakkaweo-last-provider";

export function getLastProvider(): Provider | null {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === "kakao" || value === "google" || value === "naver" || value === "local") {
      return value;
    }
  } catch {
    /* localStorage 접근 불가 */
  }
  return null;
}

export function saveLastProvider(provider: Provider) {
  try {
    localStorage.setItem(STORAGE_KEY, provider);
  } catch {
    /* localStorage 접근 불가 */
  }
}

export interface ProviderColors {
  bg: string;
  text: string;
  label: string;
}

export const PROVIDER_COLORS: Record<Provider, ProviderColors> = {
  kakao: {
    bg: "bg-[#FEE500]",
    text: "text-[#191919]",
    label: "카카오",
  },
  google: {
    bg: "bg-white dark:bg-[#131314]",
    text: "text-[#1F1F1F] dark:text-[#E3E3E3]",
    label: "Google",
  },
  naver: {
    bg: "bg-[#03C75A]",
    text: "text-white",
    label: "네이버",
  },
  local: {
    bg: "bg-gray-200 dark:bg-gray-700",
    text: "text-black dark:text-white",
    label: "가까워",
  },
};
