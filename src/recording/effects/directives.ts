/* Reusable effect directives any sim can embed in its script. A sim's parser
   calls parseEffectDirective() in its default branch; its plan runs each op with
   runEffectOp(), passing a resolver that turns named targets (field names) into
   normalized coordinates. Generic numeric ops apply with no resolver.

   Script forms:
     [[notif:GitHub|CI passed|all checks green]]   slide-in notification
     [[zoom:body]]  [[zoom:0.5,0.3,1.8]]            punch in (name or cx,cy,z)
     [[zoomout]]                                    release zoom (reset)
     [[lens:0.5,0.3]] [[lens:0.5,0.3,0.18]] [[lens:off]]   spotlight
     [[cursor:send]]  [[cursor:0.8,0.1]]            move fake cursor
     [[click]]                                      click pulse
     [[arrow:0.2,0.3>0.6,0.7|look]]                 arrow with optional label
     [[string:0.2,0.3>0.6,0.7|note]]                curved connector string
     [[box:0.1,0.2,0.4,0.1|here]]                   highlight box (x,y,w,h)
     [[say:Narration line]]                         subtitle line
     [[clearfx]]                                    clear annotations + lens */

import type { EffectsController } from './EffectsProvider';

export interface AnnotSpec {
  kind: 'string' | 'arrow' | 'box';
  from?: [number, number];
  to?: [number, number];
  rect?: [number, number, number, number];
  color: string;
  curve: number;
  width: number;
  label?: string;
}

export type EffectOp =
  | { t: 'notif'; from: string; subject: string; body?: string }
  | { t: 'zoom'; coords?: { cx: number; cy: number; z: number }; name?: string }
  | { t: 'zoomout' }
  | { t: 'lens'; coords?: { x: number; y: number; r: number }; name?: string; off?: boolean }
  | { t: 'cursor'; coords?: { x: number; y: number }; name?: string }
  | { t: 'click' }
  | { t: 'annot'; ann: AnnotSpec }
  | { t: 'say'; text: string }
  | { t: 'clearfx' };

const EFFECT_HEADS = new Set([
  'notif', 'zoom', 'zoomout', 'lens', 'cursor', 'click', 'arrow', 'string', 'box', 'say', 'clearfx',
]);

export function isEffectHead(head: string): boolean {
  return EFFECT_HEADS.has(head);
}

const ACCENT = '#ffd23f';

/** Parse one [[head:arg]] into an EffectOp, or null if it isn't an effect. */
export function parseEffectDirective(head: string, arg: string): EffectOp | null {
  switch (head) {
    case 'notif': {
      const [from, subject, body] = arg.split('|');
      return { t: 'notif', from: (from || 'Mail').trim(), subject: (subject || '').trim(), body: body?.trim() || undefined };
    }
    case 'zoom': {
      const nums = arg.split(',').map((n) => parseFloat(n.trim()));
      if (nums.length >= 2 && nums.every((n) => !Number.isNaN(n))) {
        return { t: 'zoom', coords: { cx: nums[0], cy: nums[1], z: nums[2] ?? 1.6 } };
      }
      const name = arg.trim().toLowerCase();
      if (name === 'out' || name === 'reset' || name === '') return { t: 'zoomout' };
      return { t: 'zoom', name };
    }
    case 'zoomout':
      return { t: 'zoomout' };
    case 'lens': {
      const a = arg.trim().toLowerCase();
      if (a === 'off' || a === '') return { t: 'lens', off: true };
      const nums = arg.split(',').map((n) => parseFloat(n.trim()));
      if (nums.length >= 2 && !Number.isNaN(nums[0])) return { t: 'lens', coords: { x: nums[0], y: nums[1], r: nums[2] ?? 0.16 } };
      return { t: 'lens', name: a };
    }
    case 'cursor': {
      const nums = arg.split(',').map((n) => parseFloat(n.trim()));
      if (nums.length >= 2 && !Number.isNaN(nums[0])) return { t: 'cursor', coords: { x: nums[0], y: nums[1] } };
      return { t: 'cursor', name: arg.trim().toLowerCase() };
    }
    case 'click':
      return { t: 'click' };
    case 'arrow':
    case 'string': {
      const [coords, label] = arg.split('|');
      const m = coords.split('>');
      const from = pair(m[0]);
      const to = pair(m[1]);
      if (!from || !to) return null;
      return { t: 'annot', ann: { kind: head as 'arrow' | 'string', from, to, color: ACCENT, curve: head === 'string' ? 0.12 : 0, width: 0.7, label: label?.trim() || undefined } };
    }
    case 'box': {
      const [coords, label] = arg.split('|');
      const nums = coords.split(',').map((n) => parseFloat(n.trim()));
      if (nums.length < 4 || nums.some((n) => Number.isNaN(n))) return null;
      return { t: 'annot', ann: { kind: 'box', rect: [nums[0], nums[1], nums[2], nums[3]], color: ACCENT, curve: 0, width: 0.7, label: label?.trim() || undefined } };
    }
    case 'say':
      return { t: 'say', text: arg.trim() };
    case 'clearfx':
      return { t: 'clearfx' };
    default:
      return null;
  }
}

function pair(s: string | undefined): [number, number] | null {
  if (!s) return null;
  const [a, b] = s.split(',').map((n) => parseFloat(n.trim()));
  if (Number.isNaN(a) || Number.isNaN(b)) return null;
  return [a, b];
}

export interface FocusResolver {
  zoom?: (name: string) => { cx: number; cy: number; z: number } | null;
  point?: (name: string) => { x: number; y: number } | null;
}

export interface RunOpts {
  notifSound?: boolean;
  notifMs?: number;
  notifAccent?: string;
  subtitles?: boolean;          // show [[say:]] captions (default true)
  speak?: (text: string) => void; // optional TTS for [[say:]] lines
}

/** Apply an effect op to the controller. Named targets go through `resolve`. */
export function runEffectOp(ctrl: EffectsController, op: EffectOp, resolve?: FocusResolver, opts?: RunOpts): void {
  switch (op.t) {
    case 'notif':
      ctrl.notify({ from: op.from, subject: op.subject, body: op.body, accent: opts?.notifAccent }, { sound: opts?.notifSound, ms: opts?.notifMs });
      break;
    case 'zoom': {
      const c = op.coords ?? (op.name ? resolve?.zoom?.(op.name) ?? null : null);
      if (c) ctrl.setCamera({ cx: c.cx, cy: c.cy, z: c.z });
      break;
    }
    case 'zoomout':
      ctrl.setCamera(null);
      break;
    case 'lens': {
      if (op.off) { ctrl.setLens(null); break; }
      const c = op.coords ?? (op.name ? (() => { const p = resolve?.point?.(op.name!); return p ? { x: p.x, y: p.y, r: 0.16 } : null; })() : null);
      if (c) ctrl.setLens(c);
      break;
    }
    case 'cursor': {
      const c = op.coords ?? (op.name ? resolve?.point?.(op.name) ?? null : null);
      if (c) ctrl.cursorTo(c.x, c.y);
      break;
    }
    case 'click':
      ctrl.cursorClick();
      break;
    case 'annot':
      ctrl.addAnnotation(op.ann as any);
      break;
    case 'say':
      if (opts?.subtitles !== false) ctrl.setSubtitle(op.text || null);
      if (op.text && opts?.speak) opts.speak(op.text);
      break;
    case 'clearfx':
      ctrl.clearAnnotations();
      ctrl.setLens(null);
      ctrl.setSubtitle(null);
      break;
  }
}
