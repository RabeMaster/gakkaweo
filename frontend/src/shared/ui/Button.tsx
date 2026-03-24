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

const INTERACTION_CLASSES: Record<Size, { base: string; hover: string; active: string }> = {
  sm: {
    base: "shadow-brutal-sm",
    hover: "hover:shadow-brutal-sm-hover hover:translate-x-0.5 hover:translate-y-0.5",
    active: "active:shadow-none active:translate-x-[3px] active:translate-y-[3px]",
  },
  md: {
    base: "shadow-brutal",
    hover: "hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1",
    active: "active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
  },
  lg: {
    base: "shadow-brutal",
    hover: "hover:shadow-brutal-hover hover:translate-x-1 hover:translate-y-1",
    active: "active:shadow-none active:translate-x-1.5 active:translate-y-1.5",
  },
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
          ? `opacity-70 cursor-wait ${INTERACTION_CLASSES[size].base} animate-pulse`
          : isDisabled
            ? `opacity-50 cursor-not-allowed ${INTERACTION_CLASSES[size].base}`
            : `${INTERACTION_CLASSES[size].base} ${INTERACTION_CLASSES[size].hover} ${INTERACTION_CLASSES[size].active}`,
        className,
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
