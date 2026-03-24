import { useEffect } from "react";
import { RouterProvider } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { router } from "@/app/router";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { useToastStore } from "@/shared/stores/useToastStore";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function App() {
  const fetchUser = useAuthStore((s) => s.fetchUser);
  const addToast = useToastStore((s) => s.addToast);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  useEffect(() => {
    const error = new URLSearchParams(window.location.search).get("error");
    if (!error) {
      return;
    }
    addToast(`로그인에 실패했습니다: ${decodeURIComponent(error)}`, "error");
    window.history.replaceState({}, "", window.location.pathname);
  }, [addToast]);

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  );
}
