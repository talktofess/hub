import type { SimDef } from '../types';
import { Notes } from './Notes';

const defaultScript = `[[shape:sticky]][[header:Tue · 6:48 AM]]
woke up before the alarm again
the silence in this apartment is not the same as the silence at home

[[shape:notebook]][[header:Things I noticed today]]
the bakery on Main has a new sign
two old men play chess in the park even when it rains
the heron came back to the bridge

[[shape:polaroid]][[footer:the bridge, morning light]]
caught it just as the [[fag|fog]] lifted
felt like i was the only person who saw

[[shape:index]][[header:errands]]
buy bread
fix the back window
[[wirte|write]] back to mom

[[shape:postcard]][[header:dear j]][[footer:— m]]
the [[apartmnet|apartment]] is small. the window faces east. i wake up before the alarm now. tell me how you are`;

export const notesSim: SimDef = {
  id: 'notes',
  label: 'Notes',
  glyph: '🗒️',
  accent: '#f5c542',
  frame: 'free',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Notes,
};
