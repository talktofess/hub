import type { SimDef } from '../types';
import { Typewriter } from './Typewriter';
import { SimSoundTab } from '../../shell/SimSoundTab';

const defaultScript = `The Remington hummed to life.
[[pause:500]]She fed a fresh sheet past the platen,
squared the margins,
[[pause:400]]and began to [[tpye|type]].
[[pause:600]]
Every line ended the same way —
the bell, the swipe, the return.`;

export const typewriterSim: SimDef = {
  id: 'typewriter',
  label: 'Typewriter',
  glyph: '⌨️',
  accent: '#c2410c',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Typewriter,
  // mechanical strikes by default; carriage-return bell is built in.
  defaultSettings: { sound: 'typewriter' },
  settingsTabs: [{ id: 'sound', label: 'Sound', Panel: SimSoundTab }],
};
