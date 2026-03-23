import { Outlet } from "react-router-dom";
import { Header } from "@/app/layout/Header";
import { useToastStore } from "@/shared/stores/useToastStore";

const TOAST_COLORS = {
  success: "bg-green-400 border-black text-black",
  error: "bg-red-500 border-black text-white",
  info: "bg-blue-400 border-black text-white",
} as const;

export function Layout() {
  const { toasts, removeToast } = useToastStore();

  return (
    <div className="min-h-screen bg-white dark:bg-gray-950 text-black dark:text-white">
      <Header />

      <main className="max-w-5xl mx-auto px-6 py-8">
        <Outlet />
      </main>

      {toasts.length > 0 && (
        <div className="fixed bottom-6 right-6 flex flex-col gap-3 z-50">
          {toasts.map((toast) => (
            <button
              key={toast.id}
              onClick={() => removeToast(toast.id)}
              className={`border-4 ${TOAST_COLORS[toast.type]} shadow-brutal-sm px-5 py-3 font-bold text-sm transition-all duration-100 hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5`}
            >
              {toast.message}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
