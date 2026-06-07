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

/** Per-character delay (ms). Spaces/punctuation breathe; letters are quick. */
export function charDelay(ch: string): number {
  if (ch === '\n') return 220 + Math.random() * 160;
  if (ch === ' ') return 60 + Math.random() * 60;
  if (',.;:!?—'.includes(ch)) return 130 + Math.random() * 140;
  // occasional "thinking" hitch
  const base = 42 + Math.random() * 70;
  return Math.random() < 0.06 ? base + 160 + Math.random() * 200 : base;
}
