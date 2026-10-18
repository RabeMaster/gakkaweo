import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useRankingSSE } from "./useRankingSSE";

const mocks = vi.hoisted(() => ({
  generation: 0,
  invalidateQueries: vi.fn(),
  getQueryData: vi.fn(),
  cleanups: [] as (() => void)[],
}));

vi.mock("react", () => ({
  useEffect: (effect: () => () => void) => mocks.cleanups.push(effect()),
  useRef: (current: unknown) => ({ current }),
}));
vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: mocks.invalidateQueries, getQueryData: mocks.getQueryData }),
}));
vi.mock("@/shared/config/env", () => ({ API_BASE_URL: "/api" }));
vi.mock("@/shared/stores/useAuthStore", () => ({
  useAuthStore: Object.assign(
    (selector: (state: { sessionGeneration: number }) => unknown) => selector({ sessionGeneration: mocks.generation }),
    { getState: () => ({ sessionGeneration: mocks.generation }) },
  ),
}));

class TestEventSource {
  static instances: TestEventSource[] = [];
  onopen: (() => Promise<void>) | null = null;
  onerror: (() => void) | null = null;
  addEventListener = vi.fn();
  close = vi.fn();

  constructor() {
    TestEventSource.instances.push(this);
  }
}

beforeEach(() => {
  vi.useFakeTimers();
  mocks.generation = 0;
  mocks.invalidateQueries.mockReset().mockResolvedValue(undefined);
  mocks.getQueryData.mockReset().mockReturnValue({ sentenceId: "current-sentence" });
  mocks.cleanups = [];
  TestEventSource.instances = [];
  vi.stubGlobal("EventSource", TestEventSource);
});

afterEach(() => {
  mocks.cleanups.forEach((cleanup) => cleanup());
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

it("연결 복구 때 오늘 문제 조회가 끝난 뒤 랭킹을 재조회합니다", async () => {
  let resolve!: () => void;
  mocks.invalidateQueries.mockImplementationOnce(() => new Promise<void>((done) => (resolve = done)));
  useRankingSSE();

  const opened = TestEventSource.instances[0].onopen!();

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
  expect(mocks.invalidateQueries).toHaveBeenCalledWith({ queryKey: ["game", "today", 0] }, { throwOnError: true });

  resolve();
  await opened;

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2);
  expect(mocks.invalidateQueries).toHaveBeenLastCalledWith({ queryKey: ["ranking", "current-sentence"] });
});

it("오늘 문제 조회 중 로그인 세대가 바뀌면 이전 연결의 랭킹 재조회를 중단합니다", async () => {
  let resolve!: () => void;
  mocks.invalidateQueries.mockImplementationOnce(() => new Promise<void>((done) => (resolve = done)));
  useRankingSSE();
  const opened = TestEventSource.instances[0].onopen!();

  mocks.generation++;
  resolve();
  await opened;

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("오늘 문제 조회 중 연결이 끊기면 이전 연결의 늦은 랭킹 재조회를 중단합니다", async () => {
  let resolve!: () => void;
  mocks.invalidateQueries.mockImplementationOnce(() => new Promise<void>((done) => (resolve = done)));
  useRankingSSE();
  const source = TestEventSource.instances[0];
  const opened = source.onopen!();

  source.onerror!();
  resolve();
  await opened;

  expect(source.close).toHaveBeenCalledTimes(1);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("오늘 문제 조회에 실패하면 랭킹 재조회 없이 오류를 처리합니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("문제 조회 실패"));
  useRankingSSE();

  await expect(TestEventSource.instances[0].onopen!()).resolves.toBeUndefined();

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("연결을 유지한 채 오늘 문제 조회가 회복되면 새 문제의 랭킹을 조회합니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("일시적인 문제 조회 실패"));
  mocks.getQueryData.mockReturnValue({ sentenceId: "new-sentence" });
  useRankingSSE();
  const source = TestEventSource.instances[0];

  await source.onopen!();
  await vi.advanceTimersByTimeAsync(15_000);

  expect(TestEventSource.instances).toHaveLength(1);
  expect(source.close).not.toHaveBeenCalled();
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(3);
  expect(mocks.invalidateQueries).toHaveBeenNthCalledWith(
    2,
    { queryKey: ["game", "today", 0] },
    { throwOnError: true },
  );
  expect(mocks.invalidateQueries).toHaveBeenLastCalledWith({ queryKey: ["ranking", "new-sentence"] });
});

it("오늘 문제 조회가 계속 실패하면 간격을 두고 재시도하며 성공 후에는 멈춥니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("첫 실패")).mockRejectedValueOnce(new Error("두 번째 실패"));
  useRankingSSE();

  await TestEventSource.instances[0].onopen!();
  await vi.advanceTimersByTimeAsync(14_999);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);

  await vi.advanceTimersByTimeAsync(1);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2);

  await vi.advanceTimersByTimeAsync(15_000);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(4);

  await vi.advanceTimersByTimeAsync(60_000);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(4);
  expect(vi.getTimerCount()).toBe(0);
});

it("재시도를 기다리는 동안 로그인 세대가 바뀌면 이전 오늘 문제를 조회하지 않습니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("문제 조회 실패"));
  useRankingSSE();

  await TestEventSource.instances[0].onopen!();
  mocks.generation++;
  await vi.advanceTimersByTimeAsync(15_000);

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
  expect(vi.getTimerCount()).toBe(0);
});

it("연결이 끊기면 오늘 문제 재시도 타이머를 취소합니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("문제 조회 실패"));
  useRankingSSE();
  const source = TestEventSource.instances[0];

  await source.onopen!();
  source.onerror!();

  expect(vi.getTimerCount()).toBe(1);
  await vi.advanceTimersByTimeAsync(15_000);
  expect(TestEventSource.instances).toHaveLength(2);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("화면을 떠나면 오늘 문제 재시도 타이머를 취소합니다", async () => {
  mocks.invalidateQueries.mockRejectedValueOnce(new Error("문제 조회 실패"));
  useRankingSSE();

  await TestEventSource.instances[0].onopen!();
  mocks.cleanups[0]();

  expect(vi.getTimerCount()).toBe(0);
  await vi.advanceTimersByTimeAsync(15_000);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("이전 로그인 세대의 오늘 문제 조회가 늦게 실패해도 재시도를 예약하지 않습니다", async () => {
  let reject!: (reason: Error) => void;
  mocks.invalidateQueries.mockImplementationOnce(() => new Promise<void>((_, fail) => (reject = fail)));
  useRankingSSE();
  const opened = TestEventSource.instances[0].onopen!();

  mocks.generation++;
  reject(new Error("늦은 실패"));
  await opened;

  expect(vi.getTimerCount()).toBe(0);
  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(1);
});

it("재시도 응답을 기다리는 동안 로그인 세대가 바뀌면 랭킹을 갱신하지 않습니다", async () => {
  let resolve!: () => void;
  mocks.invalidateQueries
    .mockRejectedValueOnce(new Error("문제 조회 실패"))
    .mockImplementationOnce(() => new Promise<void>((done) => (resolve = done)));
  useRankingSSE();

  await TestEventSource.instances[0].onopen!();
  await vi.advanceTimersByTimeAsync(15_000);
  mocks.generation++;
  resolve();
  await vi.advanceTimersByTimeAsync(0);

  expect(mocks.invalidateQueries).toHaveBeenCalledTimes(2);
  expect(vi.getTimerCount()).toBe(0);
});
