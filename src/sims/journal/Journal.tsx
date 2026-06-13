import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseJournal } from './parseJournal';
import './journal.css';

/* A pencil scratches a journal entry across a ruled page, line by line, in a
   loose handwriting hand. A date sits in the top-right corner. */
export function Journal() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const doc = useMemo(() => parseJournal(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const lineTexts = useMemo(() => doc.ops.filter((o) => o.kind === 'line').map((o) => (o as { text: string }).text), [doc]);

  const [fields, setFields] = useState<Record<number, string>>({});
  const [active, setActive] = useState<number>(-1);
  const [dateShown, setDateShown] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // keep the freshest line in view as the page fills
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [fields, active]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { setFields({}); setActive(-1); setDateShown(false); } });
    if (doc.date) steps.push({ kind: 'reveal', fn: () => setDateShown(true), delay: 360 });

    let li = 0;
    for (const op of doc.ops) {
      if (op.kind === 'pause') { steps.push({ kind: 'pause', ms: op.ms }); continue; }
      const idx = li++;
      steps.push({
        kind: 'type',
        text: op.text,
        onUpdate: (v) => { setActive(idx); setFields((f) => ({ ...f, [idx]: v })); },
      });
      steps.push({ kind: 'pause', ms: 160 });
    }
    steps.push({ kind: 'reveal', fn: () => setActive(-1) });
    return steps;
  }

  useEffect(() => {
    if (rec.playSignal > 0) run(buildPlan);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.playSignal]);
  useEffect(() => {
    if (rec.stopSignal > 0) stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.stopSignal]);

  const showDate = preview ? !!doc.date : dateShown;
  const line = (i: number) => (preview ? settledText(lineTexts[i] ?? '') : fields[i] ?? '');
  const shownCount = preview ? lineTexts.length : Object.keys(fields).length + (active >= 0 ? 1 : 0);

  return (
    <div className="jr-root">
      <div className="jr-page" ref={scrollRef}>
        {showDate && <div className="jr-date">{doc.date}</div>}
        <div className="jr-lines">
          {lineTexts.map((_, i) => {
            if (!preview && i >= shownCount && fields[i] == null && active !== i) return null;
            return (
              <div className="jr-line" key={i}>
                {line(i)}
                {!preview && rec.playing && active === i && <span className="caret" />}
                {line(i).length === 0 && <span className="jr-nbsp">&nbsp;</span>}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
