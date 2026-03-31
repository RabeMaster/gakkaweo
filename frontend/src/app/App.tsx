import { useEffect } from "react";
import { RouterProvider } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import { router } from "@/app/router";
import { queryClient } from "@/shared/api/queryClient";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useToastStore } from "@/shared/stores/useToastStore";

export function App() {
  const fetchUser = useAuthStore((s) => s.fetchUser);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const isLoading = useAuthStore((s) => s.isLoading);
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  useEffect(() => {
    if (isAuthenticated && !isLoading) {
      queryClient.invalidateQueries({ queryKey: ["ranking"] });
    }
  }, [isAuthenticated, isLoading]);

  useEffect(() => {
    const error = new URLSearchParams(window.location.search).get("error");
    if (!error) {
      return;
    }
    addToast(`로그인에 실패했습니다: ${error}`, "error");
    window.history.replaceState({}, "", window.location.pathname);
  }, [addToast]);

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
