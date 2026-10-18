import { afterEach, expect, it, vi } from "vitest";

afterEach(() => vi.unstubAllGlobals());

it("starts and changes theme when localStorage is blocked", async () => {
  const toggle = vi.fn();
  vi.stubGlobal("localStorage", {
    getItem: () => {
      throw new Error("blocked");
    },
    setItem: () => {
      throw new Error("blocked");
    },
    removeItem: () => {
      throw new Error("blocked");
    },
  });
  vi.stubGlobal("window", { matchMedia: () => ({ matches: false, addEventListener: vi.fn() }) });
  vi.stubGlobal("document", { documentElement: { classList: { toggle } } });
  const { useThemeStore } = await import("./useThemeStore");
  expect(useThemeStore.getState().theme).toBe("system");
  expect(() => useThemeStore.getState().setTheme("dark")).not.toThrow();
  expect(useThemeStore.getState().theme).toBe("dark");
  expect(toggle).toHaveBeenLastCalledWith("dark", true);
});
