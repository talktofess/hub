import type { SimDef } from '../types';
import { IMessage } from './IMessage';

const defaultScript = `[[name:Alex]]heyy[[send]][[delivered]][[pause:1200]][[typing:1600]][[recv:hey what's up]][[react:haha]][[pause:700]]nothing much, [[witing|writing]] some code rn[[send]][[delivered]][[pause:1400]][[recv:so cool]][[pause:600]]wbu?[[send]][[pause:1500]][[react:heart]]`;

export const imessageSim: SimDef = {
  id: 'imessage',
  label: 'iMessage',
  glyph: '💬',
  accent: '#2ecc71',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: IMessage,
};
