import { create } from "zustand";

type Theme = "light" | "dark" | "system";

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

function getSystemTheme(): "light" | "dark" {
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: Theme) {
  const resolved = theme === "system" ? getSystemTheme() : theme;
  document.documentElement.classList.toggle("dark", resolved === "dark");
}

const stored = (localStorage.getItem("theme") as Theme) || "system";
applyTheme(stored);

export const useThemeStore = create<ThemeState>((set) => ({
  theme: stored,
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
