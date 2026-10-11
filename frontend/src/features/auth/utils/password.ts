export const PASSWORD_MAX_BYTES = 72;

export function isPasswordTooLong(password: string) {
  return new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES;
}

export function validatePasswordChange(
  currentPassword: string,
  newPassword: string,
  confirmation: string,
): string | null {
  if (!currentPassword) {
    return "현재 비밀번호를 입력해 주세요.";
  }
  if (isPasswordTooLong(currentPassword)) {
    return "현재 비밀번호가 너무 깁니다. 더 짧게 입력해 주세요.";
  }
  if (newPassword.length < 8) {
    return "새 비밀번호는 8자 이상 입력해 주세요.";
  }
  if (isPasswordTooLong(newPassword)) {
    return "새 비밀번호가 너무 깁니다. 더 짧게 입력해 주세요.";
  }
  if (currentPassword === newPassword) {
    return "현재 비밀번호와 다른 비밀번호를 입력해 주세요.";
  }
  if (newPassword !== confirmation) {
    return "새 비밀번호가 일치하지 않습니다.";
  }
  return null;
}
