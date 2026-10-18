import { useSyncExternalStore } from "react";

function getQuery() {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return null;
  }
  return window.matchMedia("(prefers-reduced-motion: reduce)");
}

const subscribe = (onChange: () => void) => {
  const query = getQuery();
  query?.addEventListener("change", onChange);
  return () => query?.removeEventListener("change", onChange);
};

export function useReducedMotion() {
  return useSyncExternalStore(
    subscribe,
    () => getQuery()?.matches ?? false,
    () => false,
  );
}
