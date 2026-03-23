import type { InputHTMLAttributes } from "react";

export function Input({ className = "", ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={[
        "border-4 border-black dark:border-white rounded-none bg-white dark:bg-gray-900",
        "text-black dark:text-white px-4 py-3 font-medium text-lg w-full",
        "focus:outline-none focus:ring-0 placeholder:text-gray-400",
        className,
      ].join(" ")}
      {...rest}
    />
  );
}
