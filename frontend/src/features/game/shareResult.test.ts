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

it("공유와 두 복사 방식이 모두 없는 환경은 지원 불가로 처리합니다", async () => {
  vi.stubGlobal("navigator", {});
  await expect(shareGameResult(text, url)).resolves.toBe("unsupported");
});

it.each([
  { name: "Clipboard API가 없으면 임시 입력란으로 복사합니다", clipboardAvailable: false, copied: true },
  { name: "공유와 Clipboard API가 실패하면 임시 입력란으로 복사합니다", clipboardAvailable: true, copied: true },
  { name: "임시 입력란 복사도 실패하면 오류를 전달하고 정리합니다", clipboardAvailable: true, copied: false },
])("$name", async ({ clipboardAvailable, copied }) => {
  class TestElement {
    focus = vi.fn();
  }
  const activeElement = new TestElement();
  const textarea = { value: "", readOnly: false, style: {}, focus: vi.fn(), select: vi.fn(), remove: vi.fn() };
  const append = vi.fn();
  const execCommand = vi.fn().mockReturnValue(copied);
  const writeText = vi.fn().mockRejectedValue(new DOMException("denied", "NotAllowedError"));
  vi.stubGlobal("navigator", {
    share: vi.fn().mockRejectedValue(new TypeError("unsupported")),
    ...(clipboardAvailable ? { clipboard: { writeText } } : {}),
  });
  vi.stubGlobal("HTMLElement", TestElement);
  vi.stubGlobal("document", {
    activeElement,
    createElement: vi.fn().mockReturnValue(textarea),
    body: { append },
    execCommand,
  });

  if (copied) {
    await expect(shareGameResult(text, url)).resolves.toBe("copied");
  } else {
    await expect(shareGameResult(text, url)).rejects.toThrow("게임 결과를 복사하지 못했습니다.");
  }

  expect(writeText).toHaveBeenCalledTimes(clipboardAvailable ? 1 : 0);
  expect(textarea.value).toBe(`${text}\n${url}`);
  expect(append).toHaveBeenCalledWith(textarea);
  expect(textarea.focus).toHaveBeenCalledWith({ preventScroll: true });
  expect(textarea.select).toHaveBeenCalled();
  expect(execCommand).toHaveBeenCalledWith("copy");
  expect(textarea.remove).toHaveBeenCalled();
  expect(activeElement.focus).toHaveBeenCalledWith({ preventScroll: true });
});
