import type { SimDef } from '../types';
import { Lists } from './Lists';

const defaultScript = `[[title:Trends this year, ranked]]

[[item]]
rank: 3
title: Slow zoom POV
text: Cinematic. Made everyone look good.
tier: B
score: 7.6

[[item]]
rank: 2
title: Counter-cut
text: Everyone's [[doign|doing]] it now.
tier: A
score: 8.4
badge: rising

[[item]]
rank: 1
title: The quiet edit
text: No effects. Just the moment.
tier: S
score: 9.3
badge: viral`;

export const listsSim: SimDef = {
  id: 'lists',
  label: 'Lists',
  glyph: '✅',
  accent: '#5b8def',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Lists,
};
