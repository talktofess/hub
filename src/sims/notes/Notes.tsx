import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseNotes } from './parseNotes';
import type { Note } from './parseNotes';
import './notes.css';

export function Notes() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const notes = useMemo(() => parseNotes(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [visible, setVisible] = useState(0);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [active, setActive] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  // keep the freshest card in view as it fills
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [fields, visible]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    const set = (key: string) => (v: string) => {
      setActive(key);
      setFields((f) => ({ ...f, [key]: v }));
    };
    steps.push({ kind: 'reveal', fn: () => { setVisible(0); setFields({}); setActive(null); } });
    notes.forEach((note, ni) => {
      steps.push({ kind: 'reveal', fn: () => setVisible(ni + 1), delay: 380 });
      if (note.header) steps.push({ kind: 'type', text: note.header, onUpdate: set(`${ni}:header`) });
      note.lines.forEach((ln, li) => {
        steps.push({ kind: 'type', text: ln, onUpdate: set(`${ni}:b${li}`) });
        steps.push({ kind: 'pause', ms: 140 });
      });
      if (note.footer) steps.push({ kind: 'type', text: note.footer, onUpdate: set(`${ni}:footer`) });
      steps.push({ kind: 'pause', ms: 520 });
    });
    steps.push({ kind: 'reveal', fn: () => setActive(null) });
    return steps;
  }

  // start / stop are driven by the shell via play/stop signals
  useEffect(() => {
    if (rec.playSignal > 0) run(buildPlan);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.playSignal]);
  useEffect(() => {
    if (rec.stopSignal > 0) stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.stopSignal]);

  const text = (key: string, fallback: string | undefined) => {
    if (preview) return settledText(fallback || '');
    return fields[key] ?? '';
  };
  const caret = (key: string) => (!preview && rec.playing && active === key);

  return (
    <div className="notes-root">
      <div className="notes-scroll" ref={scrollRef}>
        <div className="notes-col">
          {notes.map((note, ni) => {
            if (!preview && ni >= visible) return null;
            return <NoteCard key={ni} ni={ni} note={note} text={text} caret={caret} />;
          })}
        </div>
      </div>
    </div>
  );
}

function NoteCard({
  ni, note, text, caret,
}: {
  ni: number;
  note: Note;
  text: (key: string, fallback?: string) => string;
  caret: (key: string) => boolean;
}) {
  const Caret = () => <span className="caret" />;
  const header = note.header != null && (
    <div className="note-header">{text(`${ni}:header`, note.header)}{caret(`${ni}:header`) && <Caret />}</div>
  );
  const body = (
    <div className="note-body">
      {note.lines.map((ln, li) => (
        <div className="note-line" key={li}>
          {text(`${ni}:b${li}`, ln)}{caret(`${ni}:b${li}`) && <Caret />}
        </div>
      ))}
    </div>
  );
  const footer = note.footer != null && (
    <div className="note-footer">{text(`${ni}:footer`, note.footer)}{caret(`${ni}:footer`) && <Caret />}</div>
  );

  if (note.shape === 'polaroid') {
    return (
      <div className="note note-polaroid">
        <div className="polaroid-photo" />
        <div className="polaroid-caption">{footer || header}</div>
        {note.lines.length > 0 && <div className="polaroid-note">{body}</div>}
      </div>
    );
  }
  if (note.shape === 'postcard') {
    return (
      <div className="note note-postcard">
        <div className="postcard-left">
          {header}
          {body}
          {footer}
        </div>
        <div className="postcard-right"><div className="stamp" /><div className="lines" /></div>
      </div>
    );
  }
  return (
    <div className={`note note-${note.shape}`}>
      {header}
      {body}
      {footer}
    </div>
  );
}
