/* Parse the boot URL, and build THE single OBS URL.

   One URL carries everything OBS needs for a take — sim + script + the universal
   settings — and it's copied once into the Browser Source. Modes live in the
   hash so the URL needs no server routing:
     #present&sim=NAME&s=...&snd=...&spd=...&bg=...   the single recording URL
   (#render / #audiocap from the legacy two-pass flow are still parsed for
   backward compatibility, but the app's one-click export emits #present.) */

import { settingsFromParams, settingsToParams } from './settings';
import type { Settings } from './settings';

export type Mode = 'edit' | 'present' | 'render' | 'audiocap';

export interface BootConfig {
  mode: Mode;
  sim: string | null;
  script: string | null;
  settings: Partial<Settings>;
}

function readParams(): URLSearchParams {
  const raw = location.hash.slice(1) || location.search.slice(1);
  return new URLSearchParams(raw);
}

export function readBootConfig(): BootConfig {
  const p = readParams();
  let mode: Mode = 'edit';
  if (p.has('render')) mode = 'render';
  else if (p.has('audiocap')) mode = 'audiocap';
  else if (p.has('present')) mode = 'present';
  return {
    mode,
    sim: p.get('sim'),
    script: p.get('s'),
    settings: settingsFromParams(p),
  };
}

/** Build the single OBS URL: present mode, this sim + script + all settings. */
export function buildObsUrl(sim: string, script: string, settings: Settings): string {
  const base = location.href.replace(/[#?].*$/, '');
  const parts: string[] = ['present', 'sim=' + encodeURIComponent(sim)];
  if (script && script.trim()) parts.push('s=' + encodeURIComponent(script));
  parts.push(...settingsToParams(settings));
  return base + '#' + parts.join('&');
}
