import { getSimilarityColor } from "@/shared/utils/similarity";

interface SimilarityBadgeProps {
  similarity: number;
}

export function SimilarityBadge({ similarity }: SimilarityBadgeProps) {
  const { bg, text } = getSimilarityColor(similarity);

  return (
    <span
      className="inline-block border-2 border-black dark:border-white rounded-none px-2 py-0.5 text-sm font-bold tabular-nums"
      style={{ backgroundColor: bg, color: text }}
    >
      {similarity.toFixed(1)}%
    </span>
  );
}
