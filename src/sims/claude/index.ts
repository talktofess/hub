import type { SimDef } from '../types';
import { Claude } from './Claude';

const defaultScript = `[[model:Claude Opus 4.8]]
[[think:2800]]
write a text to my boss for why i'm late. [[beleivable|believable]]
[[reply]]
Hi Marcus — running ~20 min behind, a water main burst on Oak St and the road's taped off.

Rerouting now, in ASAP. Sorry for the scramble!`;

export const claudeSim: SimDef = {
  id: 'claude',
  label: 'Claude',
  glyph: '✳️',
  accent: '#d97757',
  frame: 'free',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Claude,
};
