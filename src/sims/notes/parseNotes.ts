/* Parse a Notes script into note cards.

   Directives (may share a line):
     [[shape:sticky|notebook|polaroid|index|postcard]]   starts a new card
     [[header:...]]   card header / title line
     [[footer:...]]   card footer / caption / sign-off
   Everything else is a body line. Body lines keep their [[wrong|right]] typo
   markup — the typewriter expands it; settledText() resolves it for preview. */

export type NoteShape = 'sticky' | 'notebook' | 'polaroid' | 'index' | 'postcard';

export interface Note {
  shape: NoteShape;
  header?: string;
  footer?: string;
  lines: string[];
}

const SHAPES: NoteShape[] = ['sticky', 'notebook', 'polaroid', 'index', 'postcard'];

export function parseNotes(script: string): Note[] {
  const notes: Note[] = [];
  let cur: Note | null = null;
  const ensure = (): Note => {
    if (!cur) { cur = { shape: 'sticky', lines: [] }; notes.push(cur); }
    return cur;
  };

  for (const raw of script.replace(/\r/g, '').split('\n')) {
    let line = raw;

    const mShape = line.match(/\[\[shape:([^\]]+)\]\]/);
    if (mShape) {
      const s = mShape[1].trim() as NoteShape;
      cur = { shape: SHAPES.includes(s) ? s : 'sticky', lines: [] };
      notes.push(cur);
      line = line.replace(/\[\[shape:[^\]]+\]\]/g, '');
    }
    const mHeader = line.match(/\[\[header:([^\]]*)\]\]/);
    if (mHeader) { ensure().header = mHeader[1]; line = line.replace(/\[\[header:[^\]]*\]\]/g, ''); }
    const mFooter = line.match(/\[\[footer:([^\]]*)\]\]/);
    if (mFooter) { ensure().footer = mFooter[1]; line = line.replace(/\[\[footer:[^\]]*\]\]/g, ''); }

    line = line.trim();
    if (line) ensure().lines.push(line);
  }
  return notes;
}
