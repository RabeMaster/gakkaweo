import { expect, it, vi, afterEach } from "vitest";
import { apiFetch } from "@/shared/api/client";
import { advanceSessionGeneration } from "@/shared/api/sessionContext";

vi.mock("@/shared/config/env", () => ({ API_BASE_URL: "/api" }));
afterEach(() => vi.unstubAllGlobals());

it("같은 계정의 새 로그인 세대에서 이전 401 응답을 갱신하거나 다시 제출하지 않습니다", async () => {
  let resolve!: (response: Response) => void;
  const fetchMock = vi.fn(
    () =>
      new Promise<Response>((done) => {
        resolve = done;
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  const request = apiFetch("/daily/guess", { method: "POST", body: { guessText: "이전 추측" } });

  advanceSessionGeneration();
  resolve(new Response(null, { status: 401 }));

  await expect(request).rejects.toMatchObject({ name: "AbortError" });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("조회가 중단되면 인증 갱신 이후 다시 요청하지 않습니다", async () => {
  const controller = new AbortController();
  let resolve!: (response: Response) => void;
  const fetchMock = vi
    .fn()
    .mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockImplementationOnce(
      () =>
        new Promise<Response>((done) => {
          resolve = done;
        }),
    );
  vi.stubGlobal("fetch", fetchMock);
  const request = apiFetch("/daily/status", { signal: controller.signal });
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  controller.abort();
  resolve(new Response(null, { status: 204 }));

  await expect(request).rejects.toMatchObject({ name: "AbortError" });
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
