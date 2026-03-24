import { apiFetch } from "@/shared/api/client";
import type { MeResponse } from "@/shared/api/types";

export function logout() {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}

export function deleteAccount() {
  return apiFetch<void>("/auth/account", { method: "DELETE" });
}

export function changeNickname(nickname: string) {
  return apiFetch<MeResponse>("/auth/nickname", {
    method: "PATCH",
    body: { nickname },
  });
}

export function register(username: string, password: string) {
  return apiFetch<MeResponse>("/auth/register", { method: "POST", body: { username, password } });
}

export function login(username: string, password: string) {
  return apiFetch<MeResponse>("/auth/login", { method: "POST", body: { username, password } });
}
