/* Pure typing primitives shared by every sim.

   A sim turns its script into a flat list of TypeSteps; the typewriter hook runs
   them. Body text may contain the legacy typo markup [[wrong|right]] — type the
   wrong spelling, pause, backspace it, type the correction — which we expand into
   low-level keystroke actions here. */

export type TypeStep =
  | { kind: 'type'; text: string; onUpdate: (partial: string) => void; cue?: number }
  | { kind: 'reveal'; fn: () => void; delay?: number }
  | { kind: 'pause'; ms: number };

export type KeyAction = { t: 'char'; ch: string } | { t: 'back' } | { t: 'wait'; ms: number };

export const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** Expand a body string (with optional [[wrong|right]] typos) into keystrokes. */
export function tokenize(text: string): KeyAction[] {
  const out: KeyAction[] = [];
  const pushChars = (s: string) => { for (const ch of Array.from(s)) out.push({ t: 'char', ch }); };
  const re = /\[\[([^\]|]*)\|([^\]]*)\]\]/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text))) {
    pushChars(text.slice(last, m.index));
    const wrong = m[1];
    const right = m[2];
    pushChars(wrong);
    out.push({ t: 'wait', ms: 240 + Math.random() * 160 });
    for (let i = 0; i < Array.from(wrong).length; i++) out.push({ t: 'back' });
    out.push({ t: 'wait', ms: 110 + Math.random() * 90 });
    pushChars(right);
    last = re.lastIndex;
  }
  pushChars(text.slice(last));
  return out;
}

/** Strip the typo markup to get the final, settled text (for instant fills). */
export function settledText(text: string): string {
  return text.replace(/\[\[([^\]|]*)\|([^\]]*)\]\]/g, (_, _w, right) => right);
}

export interface TimingOpts { jitter: number; thinkPauses: number }

/** Per-character delay (ms). jitter scales variance; thinkPauses scales the
    occasional hesitation. Spaces/punctuation breathe; letters are quick. */
export function charDelay(ch: string, o: TimingOpts): number {
  const j = 0.35 + o.jitter; // keep a little variance even at 0
  if (ch === '\n') return 170 + Math.random() * 150 * j;
  if (ch === ' ') return 52 + Math.random() * 55 * j;
  if (',.;:!?—'.includes(ch)) return 115 + Math.random() * 130 * j;
  const base = 40 + Math.random() * 65 * j;
  const hitchProb = 0.1 * o.thinkPauses;
  return Math.random() < hitchProb ? base + 150 + Math.random() * 260 * o.thinkPauses : base;
}

/** A plausible wrong neighbor key for the auto-typo effect. */
const NEIGHBORS: Record<string, string> = {
  a: 's', s: 'd', d: 'f', f: 'g', g: 'h', h: 'j', j: 'k', k: 'l', l: 'k',
  q: 'w', w: 'e', e: 'r', r: 't', t: 'y', y: 'u', u: 'i', i: 'o', o: 'p', p: 'o',
  z: 'x', x: 'c', c: 'v', v: 'b', b: 'n', n: 'm', m: 'n',
};
export function fumbleFor(ch: string): string | null {
  const lower = ch.toLowerCase();
  const n = NEIGHBORS[lower];
  if (!n) return null;
  return ch === lower ? n : n.toUpperCase();
}
