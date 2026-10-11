import { beforeEach, expect, it, vi } from "vitest";
import { apiFetch } from "@/shared/api/client";
import { changePassword, logout, logoutAll } from "./api";

vi.mock("@/shared/api/client", () => ({ apiFetch: vi.fn().mockResolvedValue(undefined) }));

beforeEach(() => vi.mocked(apiFetch).mockClear());

it("현재 기기 로그아웃은 표준 로그아웃 주소로 요청합니다", async () => {
  await logout();

  expect(apiFetch).toHaveBeenCalledWith("/auth/logout", { method: "POST" });
});

it("모든 기기 로그아웃을 현재 기기 로그아웃과 구분합니다", async () => {
  await logoutAll();

  expect(apiFetch).toHaveBeenCalledWith("/auth/logout-all", { method: "POST" });
});

it("비밀번호 변경은 현재 비밀번호 검증 값을 포함하며 본문으로만 전송합니다", async () => {
  await changePassword("CurrentTest1", "ChangedTest2");

  expect(apiFetch).toHaveBeenCalledWith("/auth/password", {
    method: "PATCH",
    body: { currentPassword: "CurrentTest1", newPassword: "ChangedTest2" },
  });
});
