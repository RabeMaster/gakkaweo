import { useEffect, useState } from "react";

function getRemaining(expiresAt: string): number {
  return new Date(expiresAt).getTime() - Date.now();
}

export function useCountdown(expiresAt: string | undefined) {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!expiresAt) {
      return;
    }
    const id = setInterval(() => {
      setTick((t) => t + 1);
    }, 1000);
    return () => clearInterval(id);
  }, [expiresAt]);

  const remaining = expiresAt ? getRemaining(expiresAt) : 0;
  const totalSeconds = Math.max(0, Math.floor(remaining / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const formatted = `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;

  return { formatted, isExpired: remaining <= 0 };
}
