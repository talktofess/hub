import type { SimDef } from '../types';
import { Journal } from './Journal';

const defaultScript = `[[date:Tuesday]]
note to self:
stop hitting snooze.[[pause:500]]
you are NOT a morning person —
but the gym closes at 9.
[[pause:600]]
figure it [[ot|out]].`;

export const journalSim: SimDef = {
  id: 'journal',
  label: 'Journal',
  glyph: '📔',
  accent: '#c0875b',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Journal,
};
