export interface GuessContext {
  sentenceId: string;
  userId: string | null;
  generation: number;
}

export function isCurrentGuessContext(submitted: GuessContext, current: GuessContext) {
  return (
    submitted.sentenceId === current.sentenceId &&
    submitted.userId === current.userId &&
    submitted.generation === current.generation
  );
}
