/* Parse the boot URL, and build the single OBS URL.

   The one OBS URL is copied once into a Browser Source. It carries the active
   sim plus a short config token (cfg) — the full config (script + all universal
   settings + media) is stored server-side and fetched by that token, so media
   and every knob reach OBS without bloating the URL:

     #present&sim=notes&cfg=ab12cd34

   If the local API is unavailable, the app falls back to inline params
   (#present&sim=...&s=...&snd=...) — lightweight settings only, no media.
   Legacy #render / #audiocap are still parsed for backward compatibility. */

import { settingsFromParams } from './settings';
import type { Settings } from './settings';

export type Mode = 'edit' | 'present' | 'render' | 'audiocap';

export interface BootConfig {
  mode: Mode;
  sim: string | null;
  script: string | null;
  cfg: string | null;
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
    cfg: p.get('cfg'),
    settings: settingsFromParams(p),
  };
}

const base = () => location.href.replace(/[#?].*$/, '');

/** The token URL — everything lives in the stored config. */
export function buildTokenUrl(sim: string, token: string): string {
  return base() + '#present&sim=' + encodeURIComponent(sim) + '&cfg=' + encodeURIComponent(token);
}

/** Inline fallback when there's no API (lightweight settings only, no media). */
export function buildInlineUrl(sim: string, script: string, params: string[]): string {
  const parts = ['present', 'sim=' + encodeURIComponent(sim)];
  if (script && script.trim()) parts.push('s=' + encodeURIComponent(script));
  parts.push(...params);
  return base() + '#' + parts.join('&');
}
