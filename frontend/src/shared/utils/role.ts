export function hasAdminAccess(role: string | undefined): boolean {
  return role === "ADMIN" || role === "SUPERADMIN";
}
