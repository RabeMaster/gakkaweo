export const SOUND_VOLUME_KEY = "sound_volume";
export const DEFAULT_VOLUME = 0.7;

export function getSoundVolume(): number {
  try {
    const v = localStorage.getItem(SOUND_VOLUME_KEY);
    return v !== null ? parseFloat(v) : DEFAULT_VOLUME;
  } catch {
    return DEFAULT_VOLUME;
  }
}
