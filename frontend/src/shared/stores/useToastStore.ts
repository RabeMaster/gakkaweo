import { create } from "zustand";

export interface Toast {
  id: string;
  message: string;
  type: "success" | "error" | "info";
  exiting: boolean;
}

interface ToastState {
  toasts: Toast[];
  addToast: (message: string, type?: Toast["type"]) => void;
  removeToast: (id: string) => void;
}

const EXIT_DURATION = 300;
const AUTO_DISMISS = 5000;

export const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],
  addToast: (message, type = "info") => {
    const id = crypto.randomUUID();
    set((state) => ({ toasts: [...state.toasts, { id, message, type, exiting: false }] }));
    setTimeout(() => get().removeToast(id), AUTO_DISMISS);
  },
  removeToast: (id) => {
    const toast = get().toasts.find((t) => t.id === id);
    if (!toast || toast.exiting) {
      return;
    }
    set((state) => ({ toasts: state.toasts.map((t) => (t.id === id ? { ...t, exiting: true } : t)) }));
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
    }, EXIT_DURATION);
  },
}));
