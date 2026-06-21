import type { SimDef } from '../types';
import { Whatsapp, DEFAULT_WA_SETTINGS } from './Whatsapp';
import { ChatTab } from './WhatsappSettings';

const defaultScript = `[[name:Maya]][[recvstory:You|Sunset run 🌇]]omg this view 😍[[send]][[read]][[pause:700]]
[[typing:1600]][[recv:right?? the whole sky went orange]][[pause:600]]
[[story:Maya|At the rooftop 🍕]]wait is that the new pizza place?[[send]][[delivered]][[pause:700]]
[[typing:1400]][[recv:yesss come through, we saved you a seat]][[react:heart]]`;

export const whatsappSim: SimDef<typeof DEFAULT_WA_SETTINGS> = {
  id: 'whatsapp',
  label: 'WhatsApp',
  glyph: '🟢',
  accent: '#25d366',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Whatsapp,
  defaultSettings: DEFAULT_WA_SETTINGS,
  settingsTabs: [{ id: 'chat', label: 'Chat', Panel: ChatTab }],
};
