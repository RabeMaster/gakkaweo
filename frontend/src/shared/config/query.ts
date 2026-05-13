export const STALE_TIME = {
  NONE: 0,
  SHORT: 30_000,
  LONG: 60_000,
  IMMUTABLE: Infinity,
} as const;

export const REFETCH_INTERVAL = {
  FAST: 15_000,
  NORMAL: 30_000,
} as const;
