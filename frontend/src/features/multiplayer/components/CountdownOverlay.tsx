import { useEffect, useState } from "react";

interface CountdownOverlayProps {
  seconds?: number;
}

export function CountdownOverlay({ seconds = 3 }: CountdownOverlayProps) {
  const [count, setCount] = useState(seconds);

  useEffect(() => {
    if (count <= 0) {
      return;
    }
    const timer = setTimeout(() => setCount(count - 1), 1000);
    return () => clearTimeout(timer);
  }, [count]);

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/60">
      <div className="text-center">
        <div
          key={count}
          className="text-[8rem] font-black text-yellow-300 leading-none animate-[countdown-pop_0.5s_ease-out]"
          style={{
            textShadow: "6px 6px 0 #000",
          }}
        >
          {count > 0 ? count : "GO!"}
        </div>
        <p className="text-xl font-black text-white mt-4">게임이 곧 시작됩니다</p>
      </div>
    </div>
  );
}
