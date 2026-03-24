import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "danger";
type Size = "sm" | "md" | "lg";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  isLoading?: boolean;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: "bg-yellow-300 text-black",
  secondary: "bg-white dark:bg-gray-900 text-black dark:text-white",
  danger: "bg-red-500 text-white",
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: "px-3 py-1.5 text-sm",
  md: "px-6 py-3 text-base",
  lg: "px-8 py-4 text-lg",
};

export function Button({
  variant = "primary",
  size = "md",
  isLoading = false,
  className = "",
  children,
  disabled,
  ...rest
}: ButtonProps) {
  const isDisabled = disabled || isLoading;

  return (
    <button
      type="button"
      disabled={isDisabled}
      className={[
        "border-4 border-black dark:border-white rounded-none font-bold transition-all duration-100",
        VARIANT_CLASSES[variant],
        SIZE_CLASSES[size],
        isLoading
          ? "opacity-70 cursor-wait shadow-brutal animate-pulse"
          : "shadow-brutal hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1 active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
        "disabled:opacity-50 disabled:cursor-not-allowed disabled:translate-x-0 disabled:translate-y-0",
        className,
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
