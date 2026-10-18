import { afterEach, expect, it, vi } from "vitest";
import { shareGameResult } from "./shareResult";

afterEach(() => vi.unstubAllGlobals());

const text = "오늘의 결과 3회 / 100.0%";
const url = "https://example.com/";

it("기본 공유가 성공하면 클립보드에 중복 복사하지 않습니다", async () => {
  const share = vi.fn().mockResolvedValue(undefined);
  const writeText = vi.fn();
  vi.stubGlobal("navigator", { share, clipboard: { writeText } });

  await expect(shareGameResult(text, url)).resolves.toBe("shared");

  expect(share).toHaveBeenCalledWith({ title: "가까워", text, url });
  expect(writeText).not.toHaveBeenCalled();
});

it.each([new TypeError("unsupported data"), new DOMException("denied", "NotAllowedError")])(
  "기본 공유가 실패하면 결과와 링크를 클립보드에 복사합니다: %s",
  async (error) => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { share: vi.fn().mockRejectedValue(error), clipboard: { writeText } });

    await expect(shareGameResult(text, url)).resolves.toBe("copied");

    expect(writeText).toHaveBeenCalledWith(`${text}\n${url}`);
  },
);

it("공유 취소는 클립보드 복사나 오류로 처리하지 않습니다", async () => {
  const writeText = vi.fn();
  vi.stubGlobal("navigator", {
    share: vi.fn().mockRejectedValue(new DOMException("cancelled", "AbortError")),
    clipboard: { writeText },
  });

  await expect(shareGameResult(text, url)).resolves.toBe("cancelled");

  expect(writeText).not.toHaveBeenCalled();
});

it("공유 호출이 동기적으로 예외를 던져도 클립보드로 전환합니다", async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", {
    share: () => {
      throw new TypeError("unsupported");
    },
    clipboard: { writeText },
  });
  await expect(shareGameResult(text, url)).resolves.toBe("copied");
  expect(writeText).toHaveBeenCalledWith(`${text}\n${url}`);
});

it("기본 공유를 지원하지 않으면 클립보드를 사용합니다", async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { clipboard: { writeText } });

  await expect(shareGameResult(text, url)).resolves.toBe("copied");

  expect(writeText).toHaveBeenCalledWith(`${text}\n${url}`);
});

it("공유와 클립보드를 모두 지원하지 않으면 지원 불가로 처리합니다", async () => {
  vi.stubGlobal("navigator", {});
  await expect(shareGameResult(text, url)).resolves.toBe("unsupported");
});

it("복사도 거절되면 호출자에게 오류를 전달합니다", async () => {
  const error = new DOMException("denied", "NotAllowedError");
  vi.stubGlobal("navigator", {
    share: vi.fn().mockRejectedValue(new TypeError("unsupported")),
    clipboard: { writeText: vi.fn().mockRejectedValue(error) },
  });
  await expect(shareGameResult(text, url)).rejects.toBe(error);
});
