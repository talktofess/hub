import type { SimDef } from '../types';
import { Email } from './Email';
import { DEFAULT_EMAIL_SETTINGS, emailLogical } from './settings';
import type { EmailSettings } from './settings';
import { LayoutTab, GmailTab, ComposeTab, InboxTab, CameraTab, NotifTab } from './EmailSettings';

const defaultScript = `[[from:Jordan Reyes]][[account:jordan@brightlabs.io]][[to]]sarah@meridian.co[[subject]]Following up on the demo[[body]]Hi Sarah,

Thanks again for the call earlier — really [[exited|excited]] about where this could go.

I've attached the updated deck. Could we grab 30 minutes on Thursday to walk through the rollout plan?

Best,
Jordan[[send]][[pause:1400]][[notif:Sarah Lin|Re: Following up on the demo|Thursday 2pm works — see you then!]][[pause:2200]]`;

export const emailSim: SimDef<EmailSettings> = {
  id: 'email',
  label: 'Email',
  glyph: '✉️',
  accent: '#ea4335',
  frame: 'phone',
  logical: { w: 1080, h: 1920 },
  ready: true,
  defaultScript,
  Component: Email,
  defaultSettings: DEFAULT_EMAIL_SETTINGS,
  getLogical: emailLogical,
  settingsTabs: [
    { id: 'layout', label: 'Layout', Panel: LayoutTab },
    { id: 'gmail', label: 'Gmail', Panel: GmailTab },
    { id: 'compose', label: 'Compose', Panel: ComposeTab },
    { id: 'inbox', label: 'Inbox', Panel: InboxTab },
    { id: 'camera', label: 'Camera', Panel: CameraTab },
    { id: 'notif', label: 'Notif', Panel: NotifTab },
  ],
};
