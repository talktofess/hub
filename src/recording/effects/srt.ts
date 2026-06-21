/* Minimal SRT parser → timed cues. Drives a caption track on the take clock
   (independent of typing), for pre-written narration scripts. */

export interface Cue { start: number; end: number; text: string } // seconds

const TIME = /(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})/;

function toSeconds(m: RegExpMatchArray): number {
  return +m[1] * 3600 + +m[2] * 60 + +m[3] + +m[4] / 1000;
}

export function parseSrt(src: string): Cue[] {
  const cues: Cue[] = [];
  const blocks = src.replace(/\r/g, '').trim().split(/\n\n+/);
  for (const b of blocks) {
    const lines = b.split('\n');
    const tline = lines.find((l) => l.includes('-->'));
    if (!tline) continue;
    const parts = tline.split('-->');
    const s = parts[0].match(TIME);
    const e = parts[1] && parts[1].match(TIME);
    if (!s || !e) continue;
    const text = lines.slice(lines.indexOf(tline) + 1).join('\n').trim();
    if (text) cues.push({ start: toSeconds(s), end: toSeconds(e), text });
  }
  return cues.sort((a, b) => a.start - b.start);
}

export function activeCue(cues: Cue[], t: number): string | null {
  for (const c of cues) if (t >= c.start && t <= c.end) return c.text;
  return null;
}
