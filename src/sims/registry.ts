import type { SimDef } from './types';
import { notesSim } from './notes';
import { imessageSim } from './imessage';
import { emailSim } from './email';
import { listsSim } from './lists';
import { corporateSim } from './corporate';
import { typerSim } from './typer';
import { tiktokSim } from './tiktok';
import { claudeSim } from './claude';
import { journalSim } from './journal';

export const SIMS: SimDef[] = [
  notesSim,
  imessageSim,
  emailSim,
  listsSim,
  corporateSim,
  typerSim,
  tiktokSim,
  claudeSim,
  journalSim,
];

export function getSim(id: string): SimDef | undefined {
  return SIMS.find((s) => s.id === id);
}

export function defaultSimId(): string {
  const stored = (() => { try { return localStorage.getItem('hub:current'); } catch { return null; } })();
  if (stored && getSim(stored)?.ready) return stored;
  return SIMS.find((s) => s.ready)?.id ?? SIMS[0].id;
}
