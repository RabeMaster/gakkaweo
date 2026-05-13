interface DefaultAvatarProps {
  size?: "sm" | "md" | "lg";
}

const SIZE_CONFIG = {
  sm: { container: "w-7 h-7 border-2", svg: 14 },
  md: { container: "w-16 h-16 border-4", svg: 32 },
  lg: { container: "w-24 h-24 border-4", svg: 48 },
} as const;

export function DefaultAvatar({ size = "sm" }: DefaultAvatarProps) {
  const { container, svg } = SIZE_CONFIG[size];
  return (
    <div
      className={`${container} border-black dark:border-white bg-gray-200 dark:bg-gray-800 flex items-center justify-center shrink-0`}
    >
      <svg
        width={svg}
        height={svg}
        viewBox="0 0 24 24"
        fill="currentColor"
        className="text-gray-400 dark:text-gray-500"
        aria-hidden="true"
      >
        <circle cx="12" cy="9" r="3.5" />
        <path d="M12 14c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5z" />
      </svg>
    </div>
  );
}
