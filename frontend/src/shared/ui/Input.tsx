import type { InputHTMLAttributes, Ref } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  ref?: Ref<HTMLInputElement>;
}

export function Input({ className = "", ref, ...rest }: InputProps) {
  return (
    <input
      ref={ref}
      className={[
        "border-4 border-black dark:border-white rounded-none shadow-brutal bg-white dark:bg-gray-900",
        "text-black dark:text-white px-4 py-3 font-medium text-lg w-full",
        "focus:outline-none focus:ring-0 focus:border-black dark:focus:border-white placeholder:text-gray-400",
        className,
      ].join(" ")}
      {...rest}
    />
  );
}
