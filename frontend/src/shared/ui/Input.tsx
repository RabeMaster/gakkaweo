import type { InputHTMLAttributes, Ref } from "react";

type Size = "sm" | "md";

interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, "size"> {
  ref?: Ref<HTMLInputElement>;
  size?: Size;
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: "shadow-brutal-sm px-3 py-1.5 text-sm",
  md: "shadow-brutal px-4 py-3 text-lg",
};

export function Input({ className = "", size = "md", ref, ...rest }: InputProps) {
  return (
    <input
      ref={ref}
      spellCheck={false}
      className={[
        "border-4 border-black dark:border-white rounded-none bg-white dark:bg-gray-900",
        "text-black dark:text-white font-medium w-full",
        "focus:outline-none focus:ring-0 focus:border-indigo-500 dark:focus:border-indigo-400 placeholder:text-gray-400",
        SIZE_CLASSES[size],
        className,
      ].join(" ")}
      {...rest}
    />
  );
}
