/* Parse a Corporate script — a long-form essay / memo typed out, heading by
   heading, paragraph by paragraph.

     [[title:...]]      the piece title (big)
     [[subtitle:...]]   a kicker under the title
     [[byline:...]]     author line
     [[h2:...]] [[h3:...]]  section headings
     [[quote:text|who]] a pull-quote (the |who attribution is optional)
     [[pause:600]]      beat between blocks

   Plain lines accumulate into paragraphs; a blank line ends the paragraph.
   Inline *emphasis* and **strong** are kept as literal text (typed as written).
   Typo markup [[wrong|right]] passes through to the typewriter. */

export type BlockTag = 'title' | 'subtitle' | 'byline' | 'h2' | 'h3' | 'quote' | 'para';

export type CorporateOp =
  | { kind: 'block'; tag: BlockTag; text: string; attr?: string }
  | { kind: 'pause'; ms: number };

const TAGS = new Set(['title', 'subtitle', 'byline', 'h1', 'h2', 'h3', 'quote']);

export function parseCorporate(script: string): CorporateOp[] {
  const ops: CorporateOp[] = [];
  let para: string[] = [];
  const flushPara = () => {
    if (para.length) { ops.push({ kind: 'block', tag: 'para', text: para.join('\n').trim() }); para = []; }
  };

  for (const raw of script.split('\n')) {
    const m = raw.match(/^\s*\[\[\s*([a-z0-9]+)\s*:([\s\S]*?)\]\]\s*$/i);
    if (m && (TAGS.has(m[1].toLowerCase()) || m[1].toLowerCase() === 'pause')) {
      const tag = m[1].toLowerCase();
      const arg = m[2];
      flushPara();
      if (tag === 'pause') {
        ops.push({ kind: 'pause', ms: clampMs(arg, 600) });
      } else if (tag === 'quote') {
        const bar = arg.indexOf('|');
        const text = bar >= 0 ? arg.slice(0, bar) : arg;
        const attr = bar >= 0 ? arg.slice(bar + 1).trim() : undefined;
        ops.push({ kind: 'block', tag: 'quote', text: text.trim(), attr });
      } else {
        ops.push({ kind: 'block', tag: (tag === 'h1' ? 'h2' : tag) as BlockTag, text: arg.trim() });
      }
      continue;
    }
    if (raw.trim() === '') { flushPara(); continue; }
    para.push(raw);
  }
  flushPara();
  return ops;
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
