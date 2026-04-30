import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { hasAdminAccess } from "@/shared/utils/role";

function LoadingFallback() {
  return <div className="max-w-md mx-auto py-12 text-center text-gray-400 font-bold animate-pulse">로딩 중...</div>;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return <LoadingFallback />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export function RequireAdminAuth({ children }: { children: ReactNode }) {
  const { user, isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return <LoadingFallback />;
  }

  if (!isAuthenticated || !hasAdminAccess(user?.role)) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export function RedirectIfAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return <LoadingFallback />;
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
}
