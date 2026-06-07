/* Universal settings — global across every sim (NOT per-sim). These knobs plus
   the active sim's script are everything a single OBS URL needs to reproduce a
   take. Stored under one localStorage key and (de)serialized to URL params. */

export type SoundProfile = 'mechanical' | 'typewriter' | 'soft' | 'tactile' | 'none';

export const SOUND_PROFILES: { id: SoundProfile; label: string }[] = [
  { id: 'mechanical', label: 'Mechanical (clicky)' },
  { id: 'typewriter', label: 'Typewriter' },
  { id: 'soft', label: 'Soft (laptop)' },
  { id: 'tactile', label: 'Tactile (thock)' },
  { id: 'none', label: 'No sound' },
];

export const SPEED_PRESETS: { label: string; value: number }[] = [
  { label: 'Slow', value: 0.7 },
  { label: 'Natural', value: 1 },
  { label: 'Fast', value: 1.5 },
  { label: 'Turbo', value: 2.2 },
];

export interface BgSettings {
  url: string | null;
  kind: 'video' | 'audio';
  loop: boolean;
  volume: number; // 0..1
}

export interface Settings {
  sound: SoundProfile;
  volume: number; // keystroke volume 0..1
  speed: number;  // typing-rate multiplier (higher = faster)
  bg: BgSettings;
}

export const DEFAULT_SETTINGS: Settings = {
  sound: 'mechanical',
  volume: 0.85,
  speed: 1,
  bg: { url: null, kind: 'video', loop: true, volume: 0.5 },
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

/** URL params that bake the universal settings into a single OBS URL. */
export function settingsToParams(s: Settings): string[] {
  const out: string[] = [];
  if (s.sound !== DEFAULT_SETTINGS.sound) out.push('snd=' + s.sound);
  if (s.volume !== DEFAULT_SETTINGS.volume) out.push('kvol=' + round(s.volume));
  if (s.speed !== DEFAULT_SETTINGS.speed) out.push('spd=' + round(s.speed));
  if (s.bg.url && s.bg.url.indexOf('blob:') !== 0 && s.bg.url.indexOf('data:') !== 0) {
    out.push('bg=' + encodeURIComponent(s.bg.url));
    out.push('bgk=' + s.bg.kind);
    if (!s.bg.loop) out.push('bgloop=0');
    if (s.bg.volume !== DEFAULT_SETTINGS.bg.volume) out.push('bgvol=' + round(s.bg.volume));
  }
  return out;
}

export function settingsFromParams(p: URLSearchParams): Partial<Settings> {
  const out: Partial<Settings> = {};
  const snd = p.get('snd') as SoundProfile | null;
  if (snd && SOUND_PROFILES.some((x) => x.id === snd)) out.sound = snd;
  if (p.has('kvol')) out.volume = clamp01(parseFloat(p.get('kvol')!));
  if (p.has('spd')) out.speed = Math.max(0.3, Math.min(3, parseFloat(p.get('spd')!)));
  if (p.has('bg')) {
    out.bg = {
      url: p.get('bg'),
      kind: (p.get('bgk') as 'video' | 'audio') || 'video',
      loop: p.get('bgloop') !== '0',
      volume: p.has('bgvol') ? clamp01(parseFloat(p.get('bgvol')!)) : DEFAULT_SETTINGS.bg.volume,
    };
  }
  return out;
}

function round(n: number): string {
  return String(Math.round(n * 100) / 100);
}
