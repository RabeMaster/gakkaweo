import { afterEach, expect, it, vi } from "vitest";
import { apiFetch } from "@/shared/api/client";

vi.mock("@/shared/config/env", () => ({ API_BASE_URL: "/api" }));

afterEach(() => vi.unstubAllGlobals());

it("JSON 본문을 직렬화하고 Headers 객체의 헤더를 유지합니다", async () => {
  const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);

  await apiFetch("/test", { method: "POST", body: { value: 1 }, headers: new Headers({ "X-Test": "allowed" }) });

  const request = fetchMock.mock.calls[0] as unknown as [string, RequestInit];

  expect(request[1].body).toBe('{"value":1}');
  expect(new Headers(request[1].headers).get("X-Test")).toBe("allowed");
  expect(new Headers(request[1].headers).get("Content-Type")).toBe("application/json");
});

it.each([
  new Blob(["example"]),
  new URLSearchParams({ value: "example" }),
  new FormData(),
  new ArrayBuffer(4),
  new Uint8Array([1, 2]),
  new ReadableStream(),
  "plain request",
])("브라우저 기본 요청 본문과 Content-Type 처리를 유지한다", async (body) => {
  const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);

  await apiFetch("/test", { method: "POST", body });

  const request = fetchMock.mock.calls[0] as unknown as [string, RequestInit];

  expect(request[1].body).toBe(body);
  expect(new Headers(request[1].headers).has("Content-Type")).toBe(false);
});

it.each(["GET", "HEAD"])("%s 요청에는 본문을 보내지 않는다", async (method) => {
  const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);

  await expect(apiFetch("/test", { method, body: { ignored: true } })).resolves.toBeUndefined();

  const request = fetchMock.mock.calls[0] as unknown as [string, RequestInit];

  expect(request[1].body).toBeUndefined();
});

it("명시한 Content-Type과 배열 형태의 헤더를 유지합니다", async () => {
  const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetchMock);

  await apiFetch("/test", {
    method: "PATCH",
    body: { value: 1 },
    headers: [["Content-Type", "application/custom+json"]],
  });

  const request = fetchMock.mock.calls[0] as unknown as [string, RequestInit];

  expect(new Headers(request[1].headers).get("Content-Type")).toBe("application/custom+json");
});

it("잘못된 로그인 요청은 세션 갱신을 시도하지 않습니다", async () => {
  const fetchMock = vi.fn(
    async () =>
      new Response(
        JSON.stringify({ status: 401, code: "INVALID_CREDENTIALS", message: "invalid", timestamp: "2026-10-04" }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
  );
  vi.stubGlobal("fetch", fetchMock);

  await expect(
    apiFetch("/auth/login", { method: "POST", body: { username: "test", password: "invalid" } }),
  ).rejects.toMatchObject({ code: "INVALID_CREDENTIALS" });

  expect(fetchMock).toHaveBeenCalledOnce();
});

function fixtureStream() {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(new TextEncoder().encode("stream fixture"));
      controller.close();
    },
  });
}

it("스트리밍 요청에 duplex를 설정하고 소비한 본문은 다시 전송하지 않습니다", async () => {
  const fetchMock = vi.fn(async (url: string, config: RequestInit) => {
    const request = new Request(`http://localhost${url}`, config);
    expect(await request.text()).toBe("stream fixture");
    return new Response(JSON.stringify({ code: "UNAUTHORIZED", message: "fixture" }), {
      status: 401,
      headers: { "Content-Type": "application/json" },
    });
  });
  vi.stubGlobal("fetch", fetchMock);

  await expect(apiFetch("/upload", { method: "POST", body: fixtureStream() })).rejects.toMatchObject({ status: 401 });

  expect(fetchMock).toHaveBeenCalledOnce();
});

it("인증 갱신에 성공하면 스트리밍 본문을 새로 만들어 재요청합니다", async () => {
  const bodies: string[] = [];
  const bodyFactory = vi.fn(fixtureStream);
  const fetchMock = vi.fn(async (url: string, config: RequestInit) => {
    if (url.endsWith("/auth/refresh")) {
      return new Response(null, { status: 200 });
    }

    const request = new Request(`http://localhost${url}`, config);
    bodies.push(await request.text());
    return bodies.length === 1
      ? new Response(JSON.stringify({ code: "UNAUTHORIZED", message: "fixture" }), { status: 401 })
      : new Response(null, { status: 204 });
  });

  vi.stubGlobal("fetch", fetchMock);

  await expect(apiFetch("/upload", { method: "POST", bodyFactory })).resolves.toBeUndefined();

  expect(bodies).toEqual(["stream fixture", "stream fixture"]);
  expect(bodyFactory).toHaveBeenCalledTimes(2);
  expect(fetchMock).toHaveBeenCalledTimes(3);
});

it("갱신 실패 후 재요청하지 않고 GET에는 불필요한 본문을 만들지 않습니다", async () => {
  const bodyFactory = vi.fn(fixtureStream);
  const fetchMock = vi.fn(async () => new Response(JSON.stringify({ code: "UNAUTHORIZED" }), { status: 401 }));
  vi.stubGlobal("fetch", fetchMock);

  await expect(apiFetch("/upload", { method: "POST", bodyFactory })).rejects.toMatchObject({ status: 401 });

  expect(bodyFactory).toHaveBeenCalledOnce();

  fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

  await apiFetch("/test", { bodyFactory });

  expect(bodyFactory).toHaveBeenCalledOnce();
});

it("본문과 본문 생성 함수를 함께 지정하면 요청 전에 거부합니다", async () => {
  const fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
  await expect(apiFetch("/test", { method: "POST", body: "fixture", bodyFactory: fixtureStream })).rejects.toThrow(
    TypeError,
  );
  expect(fetchMock).not.toHaveBeenCalled();
});
