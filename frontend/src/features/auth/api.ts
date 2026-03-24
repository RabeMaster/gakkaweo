import { apiFetch } from "@/shared/api/client";

export function logout() {
  return apiFetch<void>("/auth/logout", { method: "POST" });
}

export function deleteAccount() {
  return apiFetch<void>("/auth/account", { method: "DELETE" });
}
