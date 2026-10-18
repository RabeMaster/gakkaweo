import { afterEach, expect, it, vi } from "vitest";
import { DEFAULT_VOLUME, getSoundVolume, getLastVolume } from "./sound";

afterEach(() => vi.unstubAllGlobals());

it.each(["NaN", "Infinity", "invalid", "0.5invalid", ""])("ignores malformed stored audio volume", (value) => {
  vi.stubGlobal("localStorage", { getItem: () => value });
  expect(getSoundVolume()).toBe(DEFAULT_VOLUME);
});

it("clamps valid numbers to the browser volume range", () => {
  vi.stubGlobal("localStorage", { getItem: () => "3" });
  expect(getSoundVolume()).toBe(1);
  expect(getLastVolume()).toBe(1);
  vi.stubGlobal("localStorage", { getItem: () => "-1" });
  expect(getSoundVolume()).toBe(0);
  expect(getLastVolume()).toBe(DEFAULT_VOLUME);
});

it("uses defaults when browser storage is blocked", () => {
  vi.stubGlobal("localStorage", {
    getItem: () => {
      throw new Error("blocked");
    },
  });
  expect(getSoundVolume()).toBe(DEFAULT_VOLUME);
});
