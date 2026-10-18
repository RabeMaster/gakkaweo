const CLEAN_PATTERN = /[^가-힣a-zA-Z0-9\p{White_Space}]/gu;
const COLLAPSE_PATTERN = /\p{White_Space}+/gu;

/**
 * BE TextNormalizer.normalize()와 동일한 로직.
 * NFC 정규화 → 특수문자 제거 → 공백 정리.
 */
export function normalizeGuessText(text: string): string {
  const nfc = text.normalize("NFC");
  const cleaned = nfc.replace(CLEAN_PATTERN, "");
  const collapsed = cleaned.replace(COLLAPSE_PATTERN, " ");
  return collapsed.trim();
}
