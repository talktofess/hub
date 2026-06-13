import type { SimDef } from '../types';
import { Corporate } from './Corporate';

const defaultScript = `[[title:The Office Is a Habit, Not a Place]]
[[subtitle:On work, buildings, and trust]]
[[byline:by Jordan Reyes]]

For most of the last century, the office was a fixed coordinate. You went there. You sat. You worked. The building itself was the proof that work was happening.

[[h2:What changed]]

The pandemic broke that proof. Suddenly the building wasn't necessary, and the work still happened.

[[quote:The office is a habit, not a place — and habits are easier to break than buildings.|Anonymous, 2024]]

[[h2:Where it leaves us]]

The honest answer is: in the middle. Hybrid is messy. Fully remote is lonely. But the conversation has shifted.

[[h3:Closing]]

The next decade isn't about offices. It's about trust.`;

export const corporateSim: SimDef = {
  id: 'corporate',
  label: 'Corporate',
  glyph: '🏢',
  accent: '#8a94a6',
  frame: 'desktop',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Corporate,
};
