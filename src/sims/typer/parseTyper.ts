/* Parse a Typer script — the simplest sim. The whole script is one block of
   text typed onto the screen; the only punctuation is:

     [[pause:600]]   lift the hands for 600ms
     [[clear]]       wipe the screen and start fresh (a new "card")

   Typo markup [[wrong|right]] passes straight through to the typewriter so it
   types the wrong word and fixes it. Anything else in [[ ]] stays literal. */

export type TyperOp =
  | { kind: 'type'; text: string }
  | { kind: 'pause'; ms: number }
  | { kind: 'clear' };

export function parseTyper(script: string): TyperOp[] {
  const ops: TyperOp[] = [];
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
    if (head === 'pause') { flush(); ops.push({ kind: 'pause', ms: clampMs(arg, 800) }); }
    else if (head === 'clear') { flush(); ops.push({ kind: 'clear' }); }
    else buf += '[[' + body + ']]'; // not a directive (e.g. typo markup) — literal
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
