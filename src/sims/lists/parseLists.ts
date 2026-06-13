/* Parse a Lists script — a ranked/countdown list that reveals one item at a
   time (TikTok "top 5" style).

     [[title:Trends, ranked]]   the list title (typed first)
     [[item]]                   start a new item; the lines under it are fields:
       rank:  1                 the number/position badge (optional)
       title: Counter-cut       the item's heading (typed)
       text:  Everyone's ...    a one-line blurb (typed; [[a|b]] typos allowed)
       score: 9.1               a score chip (optional)
       tier:  S                 a tier chip (optional)
       badge: VIRAL             a highlight badge (optional)
     [[pause:600]]              beat between items

   Field order inside an item is free; unknown keys are ignored. */

export interface ListItem {
  rank?: string;
  title: string;
  text: string;
  score?: string;
  tier?: string;
  badge?: string;
}

export type ListOp =
  | { kind: 'item'; item: ListItem }
  | { kind: 'pause'; ms: number };

export interface ListDoc {
  title: string;
  ops: ListOp[];
}

export function parseLists(script: string): ListDoc {
  let title = '';
  const ops: ListOp[] = [];
  let cur: ListItem | null = null;
  const push = () => { if (cur) { ops.push({ kind: 'item', item: cur }); cur = null; } };

  for (const raw of script.split('\n')) {
    const tm = raw.match(/^\s*\[\[\s*title:(.*?)\]\]\s*$/i);
    if (tm) { title = tm[1].trim(); continue; }
    if (/^\s*\[\[\s*item\s*\]\]\s*$/i.test(raw)) { push(); cur = { title: '', text: '' }; continue; }
    const pm = raw.match(/^\s*\[\[\s*pause:(\d+)\s*\]\]\s*$/i);
    if (pm) { push(); ops.push({ kind: 'pause', ms: clampMs(pm[1], 600) }); continue; }
    if (!cur) continue;
    const kv = raw.match(/^\s*(rank|title|text|score|tier|badge)\s*:\s*(.*)$/i);
    if (kv) {
      const key = kv[1].toLowerCase() as keyof ListItem;
      cur[key] = kv[2].trim();
    }
  }
  push();
  return { title, ops };
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
