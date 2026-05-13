export function SkeletonRow({ height = "h-8", className = "" }: { height?: string; className?: string }) {
  return <div className={`${height} bg-gray-200 dark:bg-gray-700 animate-pulse ${className}`} />;
}
