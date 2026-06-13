import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseTyper } from './parseTyper';
import './typer.css';

/* The barest sim: one block of large, centred text typed onto a calm backdrop
   (or whatever background media is set). No chrome — the words are the whole
   shot. Great for quotes, prompts, openers. */
export function Typer() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const ops = useMemo(() => parseTyper(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [text, setText] = useState('');
  const textRef = useRef('');

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { textRef.current = ''; setText(''); } });
    for (const op of ops) {
      switch (op.kind) {
        case 'type': {
          let base = '';
          steps.push({ kind: 'reveal', fn: () => { base = textRef.current; } });
          steps.push({
            kind: 'type',
            text: op.text,
            onUpdate: (v) => { textRef.current = base + v; setText(textRef.current); },
          });
          break;
        }
        case 'pause':
          steps.push({ kind: 'pause', ms: op.ms });
          break;
        case 'clear':
          steps.push({ kind: 'reveal', fn: () => { textRef.current = ''; setText(''); }, delay: 260 });
          break;
      }
    }
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

  const shown = preview ? settledText(lastCard(rec.script)) : text;
  const showCaret = !preview && rec.playing;

  return (
    <div className="ty-root">
      <div className="ty-text">
        {shown}
        {showCaret && <span className="caret" />}
      </div>
    </div>
  );
}

/* In edit preview, show the final card (text after the last [[clear]]). */
function lastCard(script: string): string {
  const parts = script.split(/\[\[\s*clear\s*\]\]/i);
  return (parts[parts.length - 1] || '').replace(/\[\[\s*pause:[^\]]*\]\]/gi, '');
}
