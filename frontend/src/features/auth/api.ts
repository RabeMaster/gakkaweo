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
