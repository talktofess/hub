/* Parse a TikTok script — captions typed over a vertical video, comparison
   style. Persistent chrome (hook banner, @handle, action rail) plus a moving
   caption that retypes for each segment.

     [[hook:trends, ranked]]        a banner across the top (optional)
     [[handle:@you|comparing...]]   the @handle and an optional sub-line
     [[place:top|bottom|center]]    where the next caption sits (default center)
     [[tags:fyp,trend2024]]         hashtag chips shown with that caption
     [[pause:600]]                  beat between captions

   Lines after a [[place]] are that caption (typed; [[a|b]] typos allowed).
   [[trigger:...]] from the old format is accepted and ignored (it was a
   recording-timing cue, not visible). */

export type Place = 'top' | 'bottom' | 'center';

export interface Segment { place: Place; tags: string[]; text: string }

export type TiktokOp =
  | { kind: 'segment'; seg: Segment }
  | { kind: 'pause'; ms: number };

export interface Tiktok {
  hook: string;
  handle: string;
  sub: string;
  ops: TiktokOp[];
}

export function parseTiktok(script: string): Tiktok {
  let hook = '';
  let handle = '@you';
  let sub = '';
  const ops: TiktokOp[] = [];
  let cur: Segment | null = null;
  let buf: string[] = [];

  const flush = () => {
    if (cur) { cur.text = buf.join('\n').trim(); ops.push({ kind: 'segment', seg: cur }); }
    cur = null; buf = [];
  };

  for (const raw of script.split('\n')) {
    const m = raw.match(/^\s*\[\[\s*([a-z]+)\s*:?([\s\S]*?)\]\]\s*$/i);
    if (m) {
      const head = m[1].toLowerCase();
      const arg = m[2];
      if (head === 'hook') { hook = arg.trim(); continue; }
      if (head === 'handle') {
        const bar = arg.indexOf('|');
        handle = (bar >= 0 ? arg.slice(0, bar) : arg).trim() || handle;
        if (bar >= 0) sub = arg.slice(bar + 1).trim();
        continue;
      }
      if (head === 'place') { flush(); cur = { place: normPlace(arg), tags: [], text: '' }; continue; }
      if (head === 'tags') { if (cur) cur.tags = arg.split(',').map((t) => t.trim()).filter(Boolean); continue; }
      if (head === 'trigger') { continue; } // legacy timing cue — ignored
      if (head === 'pause') { flush(); ops.push({ kind: 'pause', ms: clampMs(arg, 600) }); continue; }
      // unknown [[..]] (e.g. typo markup) — treat as caption text
    }
    if (raw.trim() === '' && !cur) continue;
    if (!cur) cur = { place: 'center', tags: [], text: '' };
    buf.push(raw);
  }
  flush();
  return { hook, handle, sub, ops };
}

function normPlace(s: string): Place {
  const v = s.trim().toLowerCase();
  if (v.startsWith('top')) return 'top';
  if (v.startsWith('bottom')) return 'bottom';
  return 'center';
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
