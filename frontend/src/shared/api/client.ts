import type { ErrorBody } from "@/shared/api/types";

const API_BASE = import.meta.env.VITE_API_BASE_URL;

if (!API_BASE) {
  throw new Error("VITE_API_BASE_URL 환경변수가 설정되지 않았습니다. .env.development 파일을 확인하세요.");
}

export class ApiError extends Error {
  status: number;
  code: string;
  body: ErrorBody;

  constructor(status: number, code: string, body: ErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

let refreshPromise: Promise<boolean> | null = null;

async function refreshToken(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
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

  throw new ApiError(res.status, body.code, body);
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${path}`;
  const method = options.method?.toUpperCase() ?? "GET";
  const hasBody = method !== "GET" && method !== "HEAD";

  const headers: Record<string, string> = {};
  if (hasBody) {
    headers["Content-Type"] = "application/json";
  }

  const body =
    hasBody && options.body && typeof options.body === "object" && !(options.body instanceof FormData)
      ? JSON.stringify(options.body)
      : options.body;

  const config: RequestInit = {
    ...options,
    body,
    credentials: "include",
    headers: {
      ...headers,
      ...options.headers,
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
