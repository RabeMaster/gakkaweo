import { Outlet } from "react-router-dom";
import { Header } from "@/app/layout/Header";
import { AnnouncementBanner } from "@/shared/ui/AnnouncementBanner";
import { useRankingSSE } from "@/features/ranking/hooks/useRankingSSE";
import { useToastStore } from "@/shared/stores/useToastStore";

const TOAST_COLORS = {
  success: "bg-green-400 border-black dark:border-white text-black",
  error: "bg-red-500 border-black dark:border-white text-white",
  info: "bg-blue-400 border-black dark:border-white text-white",
} as const;

export function Layout() {
  useRankingSSE();
  const { toasts, removeToast } = useToastStore();

  return (
    <div className="min-h-screen bg-white dark:bg-gray-950 text-black dark:text-white">
      <Header />

      <main className="max-w-6xl mx-auto px-6 py-8">
        <AnnouncementBanner />
        <Outlet />
      </main>

      {toasts.length > 0 && (
        <div className="fixed bottom-6 right-6 flex flex-col gap-3 z-50">
          {toasts.map((toast) => (
            <button
              type="button"
              key={toast.id}
              onClick={() => removeToast(toast.id)}
              className={`border-4 ${TOAST_COLORS[toast.type]} shadow-brutal-sm px-5 py-3 font-bold text-sm transition-all duration-100 hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5 active:shadow-none active:translate-x-[3px] active:translate-y-[3px]`}
              style={{
                animation: toast.exiting ? "toast-exit 300ms ease-in forwards" : "toast-enter 300ms ease-out",
              }}
            >
              {toast.message}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
