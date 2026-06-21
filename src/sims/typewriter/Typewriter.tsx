import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { useEffects } from '../../recording/effects/EffectsProvider';
import { runEffectOp } from '../../recording/effects/directives';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseTypewriter } from './parseTypewriter';
import './typewriter.css';

/* A real typewriter: each key strikes the page in monospace, and every line ends
   with a CARRIAGE RETURN — the bell dings, the carriage swipes back to the left
   margin, and the platen ratchets the page up a line. The carriage guide rides
   the current column (monospace, so 1 char = 1ch) and animates home on return. */
export function Typewriter() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();
  const fxc = useEffects();

  const ops = useMemo(() => parseTypewriter(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [text, setText] = useState('');
  const textRef = useRef('');

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { textRef.current = ''; setText(''); fxc.resetAll(); } });

    for (const op of ops) {
      switch (op.kind) {
        case 'type': {
          let base = '';
          steps.push({ kind: 'reveal', fn: () => { base = textRef.current; } });
          steps.push({
            kind: 'type',
            text: op.text,
            onUpdate: (v) => {
              const next = base + v;
              const prev = textRef.current;
              textRef.current = next;
              setText(next);
              // a newline was just struck → carriage return: bell + swipe home
              // (col drops to 0, so the carriage transitions back to the margin)
              if (next.length > prev.length && next.endsWith('\n')) rec.audio.cue('return');
            },
          });
          break;
        }
        case 'pause':
          steps.push({ kind: 'pause', ms: op.ms });
          break;
        case 'clear':
          steps.push({ kind: 'reveal', fn: () => { rec.audio.cue('ding'); textRef.current = ''; setText(''); }, delay: 420 });
          break;
        case 'fx':
          steps.push({ kind: 'reveal', fn: () => runEffectOp(fxc, op.op) });
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

  const shown = preview ? settledText(lastSheet(rec.script)) : text;
  const lines = shown.split('\n');
  const col = lines[lines.length - 1].length;
  const showCaret = !preview && rec.playing;

  return (
    <div className="tw-root">
      <div className="tw-machine">
        <div className="tw-platen"><span className="tw-knob left" /><span className="tw-knob right" /></div>
        <div className="tw-carriage" style={{ ['--col' as any]: col }}>
          <span className="tw-guide" />
        </div>
        <div className="tw-paper">
          <div className="tw-text">
            {lines.map((ln, i) => (
              <div className="tw-line" key={i}>
                {ln || ' '}
                {showCaret && i === lines.length - 1 && <span className="caret" />}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/* Edit preview: show the final sheet (text after the last [[clear]]). */
function lastSheet(script: string): string {
  const parts = script.split(/\[\[\s*clear\s*\]\]/i);
  return (parts[parts.length - 1] || '')
    .replace(/\[\[\s*pause:[^\]]*\]\]/gi, '')
    .replace(/\[\[\s*return\s*\]\]/gi, '\n')
    .replace(/\[\[\s*(say|zoom|zoomout|lens|cursor|click|arrow|string|box|notif|clearfx)[^\]]*\]\]/gi, '');
}
