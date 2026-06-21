/* Parse a Typewriter script. Plain text is typed onto the page; newlines (real
   or [[return]]) trigger a carriage return. Directives:

     [[pause:800]]   wait
     [[return]]      explicit carriage return (same as a newline)
     [[clear]]       roll in a fresh sheet
   plus the universal effect directives ([[say:…]], [[zoom:…]], …).

   Typo markup [[wrong|right]] stays in the text for the typewriter to expand. */

import { parseEffectDirective } from '../../recording/effects/directives';
import type { EffectOp } from '../../recording/effects/directives';

export type TwOp =
  | { kind: 'type'; text: string }
  | { kind: 'pause'; ms: number }
  | { kind: 'clear' }
  | { kind: 'fx'; op: EffectOp };

export function parseTypewriter(script: string): TwOp[] {
  const ops: TwOp[] = [];
  let buf = '';
  const flush = () => { if (buf.length) { ops.push({ kind: 'type', text: buf }); buf = ''; } };

  const re = /\[\[([^\]]*)\]\]/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(script)) !== null) {
    buf += script.slice(last, m.index);
    last = re.lastIndex;
    const body = m[1];
    const colon = body.indexOf(':');
    const head = (colon >= 0 ? body.slice(0, colon) : body).trim();
    const arg = colon >= 0 ? body.slice(colon + 1) : '';

    switch (head) {
      case 'return':
        buf += '\n';
        break;
      case 'pause':
        flush();
        ops.push({ kind: 'pause', ms: clampMs(arg, 800) });
        break;
      case 'clear':
        flush();
        ops.push({ kind: 'clear' });
        break;
      default: {
        const fx = parseEffectDirective(head, arg);
        if (fx) { flush(); ops.push({ kind: 'fx', op: fx }); break; }
        buf += '[[' + body + ']]';
        break;
      }
    }
  }
  buf += script.slice(last);
  flush();
  return ops;
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
