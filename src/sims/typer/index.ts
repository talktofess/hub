import type { SimDef } from '../types';
import { Typer } from './Typer';

const defaultScript = `the best time to start
was a year ago.[[pause:900]]

the second best time
is [[now|right now]].`;

export const typerSim: SimDef = {
  id: 'typer',
  label: 'Typer',
  glyph: '⌨️',
  accent: '#a78bfa',
  frame: 'free',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Typer,
};
