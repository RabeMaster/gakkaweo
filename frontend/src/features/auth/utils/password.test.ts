import { expect, it } from "vitest";
import { isPasswordTooLong, validatePasswordChange } from "./password";

it("영문과 한글 비밀번호에 BCrypt의 UTF-8 바이트 제한을 적용합니다", () => {
  expect(isPasswordTooLong("a".repeat(72))).toBe(false);
  expect(isPasswordTooLong("a".repeat(73))).toBe(true);

  expect(isPasswordTooLong("가".repeat(24))).toBe(false);
  expect(isPasswordTooLong("가".repeat(25))).toBe(true);

  expect(isPasswordTooLong("😀".repeat(18))).toBe(false);
  expect(isPasswordTooLong("😀".repeat(18) + "a")).toBe(true);
});

it("비밀번호 공백을 제거하지 않고 변경 조건을 검사합니다", () => {
  expect(validatePasswordChange("Current1", " newpass ", " newpass ")).toBeNull();
  expect(validatePasswordChange("", "NewPassword1", "NewPassword1")).toContain("현재 비밀번호");
  expect(validatePasswordChange("Current1", "short", "short")).toContain("8자");
  expect(validatePasswordChange("Current1", "가".repeat(25), "가".repeat(25))).toContain("너무 깁니다");
  expect(validatePasswordChange("Current1", "Current1", "Current1")).toContain("다른 비밀번호");
  expect(validatePasswordChange("Current1", "NewPassword1", "Different1")).toContain("일치하지");
});
