let generation = 0;

export function getSessionGeneration() {
  return generation;
}

export function advanceSessionGeneration() {
  return ++generation;
}
