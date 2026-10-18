export async function shareGameResult(text: string, url: string) {
  if (navigator.share) {
    try {
      await navigator.share({ title: "가까워", text, url });
      return "shared";
    } catch (error) {
      if (error instanceof Error && error.name === "AbortError") {
        return "cancelled";
      }
    }
  }
  const result = `${text}\n${url}`;
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(result);
      return "copied";
    }
  } catch {
    // Clipboard API가 거절되면 임시 입력란으로 복사합니다.
  }
  if (typeof document === "undefined" || typeof document.execCommand !== "function") {
    return "unsupported";
  }

  const activeElement = document.activeElement;
  const textarea = document.createElement("textarea");
  textarea.value = result;
  textarea.readOnly = true;
  textarea.style.position = "fixed";
  textarea.style.opacity = "0";
  document.body.append(textarea);
  try {
    textarea.focus({ preventScroll: true });
    textarea.select();
    if (!document.execCommand("copy")) {
      throw new Error("게임 결과를 복사하지 못했습니다.");
    }
    return "copied";
  } finally {
    textarea.remove();
    if (activeElement instanceof HTMLElement) {
      activeElement.focus({ preventScroll: true });
    }
  }
}
