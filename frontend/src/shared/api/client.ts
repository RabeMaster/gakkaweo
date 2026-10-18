import { getSessionGeneration } from "@/shared/api/sessionContext";
import type { ErrorBody } from "@/shared/api/types";
import { API_BASE_URL } from "@/shared/config/env";

export class ApiError extends Error {
  status: number;
  code: string;
  body: ErrorBody;
  retryAfter: number | null;

  constructor(status: number, code: string, body: ErrorBody, retryAfter: number | null = null) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.body = body;
    this.retryAfter = retryAfter;
  }
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshToken(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      credentials: "include",
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (res.ok) {
    const contentType = res.headers.get("content-type");
    if (res.status === 204 || !contentType || !contentType.includes("application/json")) {
      return undefined as T;
    }
    return res.json();
  }

  const body: ErrorBody = await res.json().catch(() => ({
    status: res.status,
    code: "UNKNOWN",
    message: res.statusText,
    timestamp: new Date().toISOString(),
  }));

  const retryAfterHeader = res.headers.get("Retry-After");
  const retryAfter = retryAfterHeader ? parseInt(retryAfterHeader, 10) : null;

  throw new ApiError(res.status, body.code, body, Number.isNaN(retryAfter) ? null : retryAfter);
}

type ApiFetchOptions = Omit<RequestInit, "body"> & {
  body?: BodyInit | object | null;
  /** 인증 갱신 후 재시도할 때도 사용할 새 요청 본문을 만듭니다. */
  bodyFactory?: () => BodyInit | null;
};

function isNativeBody(body: object): boolean {
  return (
    body instanceof FormData ||
    body instanceof Blob ||
    body instanceof URLSearchParams ||
    body instanceof ArrayBuffer ||
    ArrayBuffer.isView(body) ||
    body instanceof ReadableStream
  );
}

export async function apiFetch<T>(
  path: string,
  { body: suppliedBody, bodyFactory, ...restOptions }: ApiFetchOptions = {},
): Promise<T> {
  if (suppliedBody !== undefined && bodyFactory) {
    throw new TypeError("Specify body or bodyFactory, not both");
  }

  const generation = getSessionGeneration();
  const assertCurrent = () => {
    restOptions.signal?.throwIfAborted();
    if (generation !== getSessionGeneration()) {
      throw new DOMException("Login context changed", "AbortError");
    }
  };
  const url = `${API_BASE_URL}${path}`;
  const method = restOptions.method?.toUpperCase() ?? "GET";
  const hasBody = method !== "GET" && method !== "HEAD";

  const createConfig = (): RequestInit & { duplex?: "half" } => {
    const rawBody = hasBody ? (bodyFactory ? bodyFactory() : suppliedBody) : undefined;
    const jsonBody = rawBody !== null && typeof rawBody === "object" && !isNativeBody(rawBody);
    const headers = new Headers(restOptions.headers);

    if (jsonBody && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const body = jsonBody ? JSON.stringify(rawBody) : (rawBody as BodyInit | null | undefined);

    return {
      ...restOptions,
      body,
      credentials: "include",
      headers,
      ...(body instanceof ReadableStream ? { duplex: "half" as const } : {}),
    };
  };

  const config = createConfig();
  // 한 번 소비한 스트림은 다시 보낼 수 없으므로 새 본문을 만드는 함수가 필요합니다.
  const canRetry = !(config.body instanceof ReadableStream) || Boolean(bodyFactory);

  let res = await fetch(url, config);
  assertCurrent();

  if (
    res.status === 401 &&
    canRetry &&
    path !== "/auth/login" &&
    path !== "/auth/register" &&
    path !== "/auth/refresh"
  ) {
    if (!refreshPromise) {
      refreshPromise = refreshToken().finally(() => {
        refreshPromise = null;
      });
    }

    const refreshed = await refreshPromise;
    assertCurrent();
    if (refreshed) {
      res = await fetch(url, bodyFactory ? createConfig() : config);
    }
  }

  const result = await handleResponse<T>(res);
  assertCurrent();
  return result;
}
