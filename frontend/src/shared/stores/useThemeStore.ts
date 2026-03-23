import { create } from "zustand";

type Theme = "light" | "dark" | "system";

const VALID_THEMES: Theme[] = ["light", "dark", "system"];

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

function isValidTheme(value: string | null): value is Theme {
  return value !== null && VALID_THEMES.includes(value as Theme);
}

function getSystemTheme(): "light" | "dark" {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: Theme) {
  const resolved = theme === "system" ? getSystemTheme() : theme;
  document.documentElement.classList.toggle("dark", resolved === "dark");
}

function loadTheme(): Theme {
  const stored = localStorage.getItem("theme");
  if (isValidTheme(stored)) {
    return stored;
  }
  localStorage.removeItem("theme");
  return "system";
}

const initialTheme = loadTheme();
applyTheme(initialTheme);

export const useThemeStore = create<ThemeState>((set) => ({
  theme: initialTheme,
  setTheme: (theme) => {
    localStorage.setItem("theme", theme);
    applyTheme(theme);
    set({ theme });
  },
}));

window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
  const { theme } = useThemeStore.getState();
  if (theme === "system") {
    applyTheme("system");
  }
});
