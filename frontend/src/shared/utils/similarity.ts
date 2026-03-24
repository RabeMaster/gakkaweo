interface SimilarityColor {
  bg: string;
  text: string;
}

export function getSimilarityColor(similarity: number): SimilarityColor {
  const clamped = Math.max(0, Math.min(100, similarity));
  const hue = Math.round((clamped / 100) * 120);

  return { bg: `hsl(${hue}, 80%, 45%)`, text: "#ffffff" };
}
