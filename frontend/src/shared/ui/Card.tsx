import type { ReactNode } from "react";

interface CardProps {
  children: ReactNode;
  className?: string;
}

export function Card({ children, className = "" }: CardProps) {
  return (
    <div
      className={[
        "border-4 border-black dark:border-white rounded-none shadow-brutal bg-white dark:bg-gray-900 p-6",
        className,
      ].join(" ")}
    >
      {children}
    </div>
  );
}
