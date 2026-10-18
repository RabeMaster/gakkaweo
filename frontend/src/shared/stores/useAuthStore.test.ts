import { beforeEach, expect, it, vi } from "vitest";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { apiFetch } from "@/shared/api/client";
import { queryClient } from "@/shared/api/queryClient";

vi.mock("@/shared/api/client", () => ({ apiFetch: vi.fn() }));
vi.mock("@/shared/api/queryClient", () => ({ queryClient: { clear: vi.fn() } }));

beforeEach(() => {
  vi.stubGlobal("document", { cookie: "has_session=true" });
  useAuthStore.getState().clearUser();
  vi.mocked(apiFetch).mockReset();
  vi.mocked(queryClient.clear).mockClear();
});

it("새 계정 인증 후 늦게 도착한 이전 계정의 내 정보 조회 실패는 무시합니다", async () => {
  let rejectOlder!: (error: Error) => void;

  vi.mocked(apiFetch)
    .mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectOlder = reject;
        }),
    )
    .mockResolvedValueOnce({ publicId: "new-user", nickname: "new", role: "USER" });

  const older = useAuthStore.getState().fetchUser();
  await useAuthStore.getState().fetchUser();

  vi.mocked(queryClient.clear).mockClear();
  rejectOlder(new Error("delayed failure"));
  await older;

  expect(useAuthStore.getState().user?.publicId).toBe("new-user");
  expect(useAuthStore.getState().isAuthenticated).toBe(true);
  expect(queryClient.clear).not.toHaveBeenCalled();
});

it("계정 전환과 로그아웃은 쿼리를 비우고 같은 계정의 쿼리는 유지합니다", async () => {
  vi.mocked(apiFetch)
    .mockResolvedValueOnce({ publicId: "first-user", nickname: "first", role: "USER" })
    .mockResolvedValueOnce({ publicId: "first-user", nickname: "updated", role: "USER" })
    .mockResolvedValueOnce({ publicId: "second-user", nickname: "second", role: "USER" });

  await useAuthStore.getState().fetchUser();
  expect(queryClient.clear).toHaveBeenCalledTimes(1);

  await useAuthStore.getState().fetchUser();
  expect(queryClient.clear).toHaveBeenCalledTimes(1);

  await useAuthStore.getState().fetchUser();
  expect(queryClient.clear).toHaveBeenCalledTimes(2);

  useAuthStore.getState().clearUser();
  expect(queryClient.clear).toHaveBeenCalledTimes(3);
  expect(useAuthStore.getState().user).toBeNull();
});

it("최신 내 정보 조회가 실패하면 계정 쿼리를 비웁니다", async () => {
  vi.mocked(apiFetch).mockResolvedValueOnce({ publicId: "first-user", nickname: "first", role: "USER" });

  await useAuthStore.getState().fetchUser();

  vi.mocked(queryClient.clear).mockClear();
  vi.mocked(apiFetch).mockRejectedValueOnce(new Error("current failure"));

  await useAuthStore.getState().fetchUser();

  expect(useAuthStore.getState().isAuthenticated).toBe(false);
  expect(queryClient.clear).toHaveBeenCalledOnce();
});

it("로그아웃 후 이전에 시작한 요청으로 인증 상태를 복구하지 않습니다", async () => {
  let complete!: (value: unknown) => void;

  vi.mocked(apiFetch).mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );

  const pending = useAuthStore.getState().fetchUser();
  useAuthStore.getState().clearUser();
  complete({ publicId: "test-user", nickname: "테스트", profileUrl: null, role: "USER" });
  await pending;

  expect(useAuthStore.getState().isAuthenticated).toBe(false);
  expect(useAuthStore.getState().user).toBeNull();
});

it("이전 내 정보 요청이 늦게 끝나도 최신 세션을 유지합니다", async () => {
  let complete!: (value: unknown) => void;

  vi.mocked(apiFetch)
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          complete = resolve;
        }),
    )
    .mockResolvedValueOnce({ publicId: "new-user", nickname: "테스트", profileUrl: null, role: "USER" });

  const older = useAuthStore.getState().fetchUser();
  await useAuthStore.getState().fetchUser();

  complete({ publicId: "old-user", nickname: "이전", profileUrl: null, role: "USER" });
  await older;

  expect(useAuthStore.getState().user?.publicId).toBe("new-user");
});
