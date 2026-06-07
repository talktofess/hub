/* Parse the boot URL into a recording config, and build OBS-source URLs.

   Modes live in the hash (so they survive as an OBS Browser Source URL and need
   no server routing):
     #present&sim=NAME&s=...   audible preview take (legacy "present")
     #render&sim=NAME&s=...    OBS video pass: seeded, sync-marker, MUTED keystrokes
     #audiocap&sim=NAME&s=...  clean-audio pass: seeded, records keystrokes to .webm
   Plus optional &bg=, &bgk=, &bgsrt=, &bgloop= for background media + SRT pacing. */

export type Mode = 'edit' | 'present' | 'render' | 'audiocap';

export interface BootConfig {
  mode: Mode;
  sim: string | null;
  script: string | null;
  bg: string | null;
  bgKind: 'video' | 'audio' | null;
  bgLoop: boolean;
  bgSrt: string | null;
}

function readParams(): URLSearchParams {
  const raw = location.hash.slice(1) || location.search.slice(1);
  return new URLSearchParams(raw);
}

export function readBootConfig(): BootConfig {
  const p = readParams();
  let mode: Mode = 'edit';
  if (p.has('render')) mode = 'render';
  else if (p.has('audiocap')) mode = 'audiocap';
  else if (p.has('present')) mode = 'present';
  return {
    mode,
    sim: p.get('sim'),
    script: p.get('s'),
    bg: p.get('bg'),
    bgKind: (p.get('bgk') as 'video' | 'audio' | null) || null,
    bgLoop: p.get('bgloop') !== '0',
    bgSrt: p.get('bgsrt'),
  };
}

export interface ObsUrlOpts {
  sim: string;
  script: string;
  mode?: 'present' | 'render' | 'audiocap';
  bg?: string | null;
  bgKind?: 'video' | 'audio' | null;
  bgLoop?: boolean;
  bgSrt?: string | null;
}

export function buildObsUrl(o: ObsUrlOpts): string {
  const base = location.href.replace(/[#?].*$/, '');
  const parts: string[] = [o.mode || 'present', 'sim=' + encodeURIComponent(o.sim)];
  if (o.script && o.script.trim()) parts.push('s=' + encodeURIComponent(o.script));
  if (o.bg && o.bg.indexOf('blob:') !== 0 && o.bg.indexOf('data:') !== 0) {
    parts.push('bg=' + encodeURIComponent(o.bg));
    parts.push('bgk=' + (o.bgKind || 'video'));
  }
  if (o.bgSrt) {
    const sj = o.bgSrt;
    if (sj.length < 6000) parts.push('bgsrt=' + encodeURIComponent(sj));
  }
  if (o.bgLoop === false) parts.push('bgloop=0');
  return base + '#' + parts.join('&');
}
