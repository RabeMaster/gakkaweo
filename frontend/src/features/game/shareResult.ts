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
  if (!navigator.clipboard?.writeText) {
    return "unsupported";
  }
  await navigator.clipboard.writeText(`${text}\n${url}`);
  return "copied";
}
