import type { SimDef } from '../types';
import { Email } from './Email';

const defaultScript = `[[from:Jordan Reyes]][[account:jordan@brightlabs.io]][[to]]sarah@meridian.co[[subject]]Following up on the demo[[body]]Hi Sarah,

Thanks again for the call earlier — really [[exited|excited]] about where this could go.

I've attached the updated deck. Could we grab 30 minutes on Thursday to walk through the rollout plan?

Best,
Jordan[[send]][[pause:1400]]`;

export const emailSim: SimDef = {
  id: 'email',
  label: 'Email',
  glyph: '✉️',
  accent: '#ea4335',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Email,
};
