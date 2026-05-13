export function hasAdminAccess(role: string | undefined): boolean {
  return role === "ADMIN" || role === "SUPERADMIN";
}

export function isSuperAdmin(role: string | undefined): boolean {
  return role === "SUPERADMIN";
}
