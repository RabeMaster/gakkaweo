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

type ApiFetchOptions = Omit<RequestInit, "body"> & { body?: BodyInit | object | null };

export async function apiFetch<T>(path: string, { body: rawBody, ...restOptions }: ApiFetchOptions = {}): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const method = restOptions.method?.toUpperCase() ?? "GET";
  const hasBody = method !== "GET" && method !== "HEAD";

  const headers: Record<string, string> = {};
  if (hasBody && !(rawBody instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  const body: BodyInit | null | undefined =
    hasBody && rawBody && typeof rawBody === "object" && !(rawBody instanceof FormData)
      ? JSON.stringify(rawBody)
      : (rawBody as BodyInit | null | undefined);

  const config: RequestInit = {
    ...restOptions,
    body,
    credentials: "include",
    headers: {
      ...headers,
      ...restOptions.headers,
    },
  };

  let res = await fetch(url, config);

  if (res.status === 401) {
    if (!refreshPromise) {
      refreshPromise = refreshToken().finally(() => {
        refreshPromise = null;
      });
    }

    const refreshed = await refreshPromise;
    if (refreshed) {
      res = await fetch(url, config);
    }
  }

  return handleResponse<T>(res);
}
