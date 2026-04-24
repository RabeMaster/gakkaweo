export const SOUND_VOLUME_KEY = "sound_volume";
export const SOUND_LAST_VOLUME_KEY = "sound_last_volume";
export const DEFAULT_VOLUME = 0.7;

export type SoundType = "clear" | "fail";

export const SOUND_PATHS: Record<SoundType, string> = {
  clear: "/sounds/clear.mp3",
  fail: "/sounds/faa.mp3",
};

let currentAudio: HTMLAudioElement | null = null;

export function getSoundVolume(): number {
  try {
    const v = localStorage.getItem(SOUND_VOLUME_KEY);
    return v !== null ? parseFloat(v) : DEFAULT_VOLUME;
  } catch {
    return DEFAULT_VOLUME;
  }
}

export function getLastVolume(): number {
  try {
    const v = localStorage.getItem(SOUND_LAST_VOLUME_KEY);
    if (v === null) {
      return DEFAULT_VOLUME;
    }
    const parsed = parseFloat(v);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_VOLUME;
  } catch {
    return DEFAULT_VOLUME;
  }
}

export function setLastVolume(v: number): void {
  if (!(v > 0)) {
    return;
  }
  try {
    localStorage.setItem(SOUND_LAST_VOLUME_KEY, String(v));
  } catch {
    // 스토리지 접근 불가 시 무시
  }
}

export function playSound(type: SoundType, options?: { stopPrevious?: boolean }): void {
  const volume = getSoundVolume();
  if (volume <= 0) {
    return;
  }
  if (options?.stopPrevious) {
    stopCurrentSound();
  }
  const audio = new Audio(SOUND_PATHS[type]);
  audio.volume = volume;
  audio.onended = () => {
    if (currentAudio === audio) {
      currentAudio = null;
    }
  };
  audio.play().catch(() => {});
  currentAudio = audio;
}

export function stopCurrentSound(): void {
  if (currentAudio) {
    currentAudio.pause();
    currentAudio.currentTime = 0;
    currentAudio = null;
  }
}

export function setCurrentSoundVolume(volume: number): void {
  if (currentAudio) {
    currentAudio.volume = volume;
  }
}
