import { useCallback, useMemo } from 'react';
import { useRecording } from './useRecording';
import { getSim } from '../sims/registry';

/* Read/write the active (or a named) sim's own settings. Returns the sim's
   defaults overlaid with the stored diff, plus a setter that persists only the
   diff. Sim setting panels in the drawer use this; it travels to OBS inside the
   universal token config automatically. */
export function useSimSettings<T extends object = Record<string, unknown>>(
  simId?: string,
): [T, (patch: Partial<T>) => void] {
  const rec = useRecording();
  const id = simId ?? rec.simId;
  const def = getSim(id);
  const defaults = (def?.defaultSettings ?? {}) as T;
  const stored = rec.settings.sim?.[id] as Partial<T> | undefined;

  const merged = useMemo<T>(() => ({ ...defaults, ...(stored || {}) }), [defaults, stored]);
  const set = useCallback((patch: Partial<T>) => rec.setSimSettings(id, patch), [rec, id]);
  return [merged, set];
}
