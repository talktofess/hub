/* Email-specific settings — the sim's own control surface (Layout / Gmail /
   Compose / Inbox tabs in the drawer). Stored per-sim and carried to OBS inside
   the universal token config. */

export interface InboxRow {
  from: string;
  subject: string;
  snippet: string;
  time: string;
  unread: boolean;
}

export type EmailLayout = 'desktop' | 'reel';
export type GmailTheme = 'light' | 'dark';
export type ComposePos = 'br' | 'center';

export interface EmailSettings {
  layout: EmailLayout;
  theme: GmailTheme;
  accent: string;          // Gmail action accent (Send button, links)
  sidebar: boolean;        // desktop: show the left rail
  density: number;         // desktop chrome zoom 0.8..1.2
  // compose
  composePos: ComposePos;  // desktop popup dock
  showCc: boolean;
  // inbox
  inbox: InboxRow[];
  // camera — auto-zoom that follows the active field
  camFollow: boolean;
  camZoom: number;     // zoom level when following (1..2.4)
  camPersist: boolean; // keep the zoomed-in look between fields (true = persists)
  cursor: boolean;     // a fake cursor moves to each field / Send
  // notifications
  notifSound: boolean;
  notifMs: number;     // how long each toast stays
  // sim sound override convention keys may also live here (unused by default)
  [k: string]: unknown;
}

export const DEFAULT_INBOX: InboxRow[] = [
  { from: 'GitHub', subject: 'Your CI run passed', snippet: 'All checks have passed on main — deploy is green.', time: '9:02 AM', unread: true },
  { from: 'Linear', subject: 'ENG-482 moved to In Review', snippet: 'Sarah moved “OBS recording pipeline” to In Review.', time: '8:41 AM', unread: true },
  { from: 'Figma', subject: 'Maya commented on Hub v2', snippet: '“Love the new compose flow — can we tighten the…”', time: 'Yesterday', unread: false },
  { from: 'Calendar', subject: 'Demo with Meridian — Thu 2pm', snippet: 'Reminder: 30 minute walkthrough of the rollout plan.', time: 'Yesterday', unread: false },
  { from: 'Vercel', subject: 'Deployment ready', snippet: 'sim-hub-2 deployed to production in 38s.', time: 'Mon', unread: false },
];

export const DEFAULT_EMAIL_SETTINGS: EmailSettings = {
  layout: 'reel',
  theme: 'light',
  accent: '#0b57d0',
  sidebar: true,
  density: 1,
  composePos: 'br',
  showCc: false,
  inbox: DEFAULT_INBOX,
  camFollow: false,
  camZoom: 1.5,
  camPersist: true,
  cursor: false,
  notifSound: true,
  notifMs: 4200,
};

export function emailLogical(s: EmailSettings): { w: number; h: number } {
  return s.layout === 'desktop' ? { w: 1920, h: 1080 } : { w: 1080, h: 1920 };
}

/** Normalized focus regions per layout/field, for auto-zoom + cursor + lens. */
export function emailFocus(s: EmailSettings, field: 'to' | 'cc' | 'subject' | 'body' | 'send'):
  { cx: number; cy: number; z: number; x: number; y: number } {
  const z = s.camZoom;
  if (s.layout === 'desktop') {
    // everything happens in the compose popup; nudge focus within it per field
    const docked = s.composePos === 'br';
    const cx = docked ? 0.79 : 0.5;
    const map: Record<string, number> = { to: 0.52, cc: 0.56, subject: 0.6, body: 0.74, send: 0.96 };
    const cy = map[field] ?? 0.7;
    return { cx, cy, z, x: field === 'send' ? cx - 0.06 : cx, y: cy };
  }
  // reel
  const map: Record<string, { cx: number; cy: number }> = {
    to: { cx: 0.5, cy: 0.21 },
    cc: { cx: 0.5, cy: 0.25 },
    subject: { cx: 0.5, cy: s.showCc ? 0.31 : 0.27 },
    body: { cx: 0.5, cy: 0.6 },
    send: { cx: 0.78, cy: 0.085 },
  };
  const p = map[field] ?? { cx: 0.5, cy: 0.5 };
  return { cx: p.cx, cy: p.cy, z, x: p.cx, y: p.cy };
}
