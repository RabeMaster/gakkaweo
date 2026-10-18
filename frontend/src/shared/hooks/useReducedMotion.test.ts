import { afterEach, expect, it, vi } from "vitest";
import { useReducedMotion } from "./useReducedMotion";

const mocks = vi.hoisted(() => ({
  subscribe: null as null | ((onChange: () => void) => () => void),
  serverSnapshot: null as null | (() => boolean),
}));

vi.mock("react", () => ({
  useSyncExternalStore: (
    subscribe: (onChange: () => void) => () => void,
    snapshot: () => boolean,
    serverSnapshot: () => boolean,
  ) => {
    mocks.subscribe = subscribe;
    mocks.serverSnapshot = serverSnapshot;
    return snapshot();
  },
}));

afterEach(() => vi.unstubAllGlobals());

it("window가 없어도 불러오고 기본값을 반환합니다", () => {
  vi.stubGlobal("window", undefined);
  expect(useReducedMotion()).toBe(false);
  expect(() => mocks.subscribe!(vi.fn())()).not.toThrow();
  expect(mocks.serverSnapshot!()).toBe(false);
});

it("matchMedia가 없는 환경에서도 기본값을 반환합니다", () => {
  vi.stubGlobal("window", {});
  expect(useReducedMotion()).toBe(false);
  expect(() => mocks.subscribe!(vi.fn())()).not.toThrow();
});

it("모션 감소 설정을 읽고 변경 구독을 정리합니다", () => {
  const query = { matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() };
  const matchMedia = vi.fn().mockReturnValue(query);
  vi.stubGlobal("window", { matchMedia });
  expect(useReducedMotion()).toBe(true);
  const onChange = vi.fn();
  const cleanup = mocks.subscribe!(onChange);
  expect(matchMedia).toHaveBeenCalledWith("(prefers-reduced-motion: reduce)");
  expect(query.addEventListener).toHaveBeenCalledWith("change", onChange);
  cleanup();
  expect(query.removeEventListener).toHaveBeenCalledWith("change", onChange);
  query.matches = false;
  expect(useReducedMotion()).toBe(false);
});
