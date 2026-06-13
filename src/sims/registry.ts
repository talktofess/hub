import type { SimDef } from './types';
import { notesSim } from './notes';
import { imessageSim } from './imessage';
import { emailSim } from './email';

/* Roadmap placeholders for the sims not yet rebuilt — they show in the launcher
   as "soon" so the full roster is visible. Each becomes a real entry (ready:true
   with a Component) as it's ported from /legacy, one at a time. */
function soon(id: string, label: string, glyph: string, accent: string, frame: SimDef['frame']): SimDef {
  return { id, label, glyph, accent, frame, logical: { w: 1080, h: 1920 }, ready: false, defaultScript: '' };
}

export const SIMS: SimDef[] = [
  notesSim,
  imessageSim,
  emailSim,
  soon('lists', 'Lists', '✅', '#5b8def', 'phone'),
  soon('corporate', 'Corporate', '🏢', '#8a94a6', 'desktop'),
  soon('typer', 'Typer', '⌨️', '#a78bfa', 'free'),
  soon('tiktok', 'TikTok', '🎵', '#ff2d55', 'phone'),
  soon('claude', 'Claude', '✳️', '#d97757', 'free'),
  soon('journal', 'Journal', '📔', '#c0875b', 'phone'),
];

export function getSim(id: string): SimDef | undefined {
  return SIMS.find((s) => s.id === id);
}

export function defaultSimId(): string {
  const stored = (() => { try { return localStorage.getItem('hub:current'); } catch { return null; } })();
  if (stored && getSim(stored)?.ready) return stored;
  return SIMS.find((s) => s.ready)?.id ?? SIMS[0].id;
}
