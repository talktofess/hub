/* Parse a Journal script — handwriting scratched onto a ruled page.

     [[date:Tuesday]]   write a date in the top-right corner (optional, first)
     [[pause:600]]      lift the pencil for 600ms

   Each remaining line is written out in order. Typo markup [[wrong|right]]
   passes through to the typewriter (write it wrong, scribble it right). */

export type JournalOp =
  | { kind: 'line'; text: string }
  | { kind: 'pause'; ms: number };

export interface Journal {
  date: string;
  ops: JournalOp[];
}

export function parseJournal(script: string): Journal {
  const ops: JournalOp[] = [];
  let date = '';

  // pull out [[date:..]] / [[pause:..]] but keep line structure for everything else
  const lines = script.split('\n');
  for (const raw of lines) {
    const dm = raw.match(/^\s*\[\[\s*date:(.*?)\]\]\s*$/i);
    if (dm) { date = dm[1].trim(); continue; }
    const pm = raw.match(/^\s*\[\[\s*pause:(\d+)\s*\]\]\s*$/i);
    if (pm) { ops.push({ kind: 'pause', ms: clampMs(pm[1], 600) }); continue; }
    ops.push({ kind: 'line', text: raw });
  }
  return { date, ops };
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
