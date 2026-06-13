import type { SimDef } from '../types';
import { Tiktok } from './Tiktok';

const defaultScript = `[[hook:trends, ranked]]
[[handle:@you|comparing trends today]]

[[place:top]][[tags:fyp,trend2023,og]]
this trend started it all
back when everyone tried it

[[place:bottom]][[tags:trend2024,knockoff]]
but the 2024 version
hits [[diferent|different]] though

[[place:center]][[tags:my-take,verdict]]
honestly though
the original wins. every time.`;

export const tiktokSim: SimDef = {
  id: 'tiktok',
  label: 'TikTok',
  glyph: '🎵',
  accent: '#ff2d55',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Tiktok,
};
