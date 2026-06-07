/* Universal settings — global across every sim. The active sim's script plus
   these are everything a take needs. They travel to OBS as a short config token
   (full JSON stored server-side), so media + all knobs reach OBS without bloating
   the URL. A lightweight subset can also ride inline as a fallback when the local
   API isn't available. */

export type SoundProfile =
  | 'mechanical' | 'typewriter' | 'soft' | 'tactile'
  | 'blue' | 'vintage' | 'bubble' | 'mush' | 'none';

export const SOUND_PROFILES: { id: SoundProfile; label: string }[] = [
  { id: 'mechanical', label: 'Mechanical (clicky)' },
  { id: 'blue', label: 'Blue switch (sharp)' },
  { id: 'typewriter', label: 'Typewriter' },
  { id: 'vintage', label: 'Vintage keyboard' },
  { id: 'tactile', label: 'Tactile (thock)' },
  { id: 'soft', label: 'Soft (laptop)' },
  { id: 'mush', label: 'Marshmallow (muted)' },
  { id: 'bubble', label: 'Bubble (poppy)' },
  { id: 'none', label: 'No sound' },
];

export const SPEED_PRESETS: { label: string; value: number }[] = [
  { label: 'Slow', value: 0.7 },
  { label: 'Natural', value: 1 },
  { label: 'Fast', value: 1.5 },
  { label: 'Turbo', value: 2.2 },
];

export type DisplayMode = 'cover' | 'contain' | 'fill' | 'tile' | 'center' | 'blur-fill';
export const DISPLAY_MODES: { id: DisplayMode; label: string }[] = [
  { id: 'cover', label: 'Cover (fill, crop)' },
  { id: 'contain', label: 'Contain (whole image)' },
  { id: 'blur-fill', label: 'Blur fill (whole + blurred bg)' },
  { id: 'fill', label: 'Stretch' },
  { id: 'tile', label: 'Tile' },
  { id: 'center', label: 'Center (actual size)' },
];

export type CaretStyle = 'bar' | 'block' | 'underline';
export type ThemeMode = 'auto' | 'light' | 'dark';

export interface BackgroundSetting {
  mediaId: string | null; // from the local media library
  url: string | null;     // or a direct URL (cloud / back-compat)
  kind: 'image' | 'video' | 'audio';
  mode: DisplayMode;      // image/video framing
  kenBurns: boolean;      // slow pan-zoom for stills
  audioOnly: boolean;     // for a video: use only its audio
  loop: boolean;
  volume: number;
}

export interface Settings {
  // sound
  sound: SoundProfile;
  volume: number; // 0..1 keystroke volume
  speed: number;  // typing-rate multiplier
  // typing realism
  startDelay: number;  // ms before typing begins
  thinkPauses: number; // 0..1 hesitation amount
  jitter: number;      // 0..1 per-char timing variance
  autoTypo: number;    // 0..0.15 chance/char to fumble + self-correct
  loop: boolean;       // loop the take
  holdEnd: number;     // ms to hold the finished take before loop/stop
  // caret
  showCaret: boolean;
  caretStyle: CaretStyle;
  caretColor: string;
  caretBlink: boolean;
  // look & feel
  theme: ThemeMode;
  accent: string;
  fontScale: number; // 0.8..1.3
  grain: boolean;
  vignette: boolean;
  // media (universal)
  bg: BackgroundSetting;
}

export const DEFAULT_SETTINGS: Settings = {
  sound: 'mechanical', volume: 0.85, speed: 1,
  startDelay: 600, thinkPauses: 0.5, jitter: 0.5, autoTypo: 0, loop: false, holdEnd: 1200,
  showCaret: true, caretStyle: 'bar', caretColor: '#ffffff', caretBlink: true,
  theme: 'dark', accent: '#5b8def', fontScale: 1, grain: false, vignette: false,
  bg: { mediaId: null, url: null, kind: 'video', mode: 'cover', kenBurns: false, audioOnly: false, loop: true, volume: 0.5 },
};

const KEY = 'hub:settings';

export function mergeSettings(base: Settings, over: Partial<Settings> | undefined): Settings {
  if (!over) return base;
  return { ...base, ...over, bg: { ...base.bg, ...(over.bg || {}) } };
}

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) return mergeSettings(DEFAULT_SETTINGS, JSON.parse(raw));
  } catch { /* ignore */ }
  return DEFAULT_SETTINGS;
}

export function saveSettings(s: Settings): void {
  try { localStorage.setItem(KEY, JSON.stringify(s)); } catch { /* ignore */ }
}

const clamp01 = (n: number) => Math.max(0, Math.min(1, n));
const round = (n: number) => String(Math.round(n * 100) / 100);

/** Lightweight inline params — fallback only (no media) when the API is down. */
export function settingsToParams(s: Settings): string[] {
  const out: string[] = [];
  if (s.sound !== DEFAULT_SETTINGS.sound) out.push('snd=' + s.sound);
  if (s.volume !== DEFAULT_SETTINGS.volume) out.push('kvol=' + round(s.volume));
  if (s.speed !== DEFAULT_SETTINGS.speed) out.push('spd=' + round(s.speed));
  return out;
}

export function settingsFromParams(p: URLSearchParams): Partial<Settings> {
  const out: Partial<Settings> = {};
  const snd = p.get('snd') as SoundProfile | null;
  if (snd && SOUND_PROFILES.some((x) => x.id === snd)) out.sound = snd;
  if (p.has('kvol')) out.volume = clamp01(parseFloat(p.get('kvol')!));
  if (p.has('spd')) out.speed = Math.max(0.3, Math.min(3, parseFloat(p.get('spd')!)));
  return out;
}
