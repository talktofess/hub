import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { useSimSettings } from '../../recording/useSimSettings';
import { useEffects } from '../../recording/effects/EffectsProvider';
import { runEffectOp } from '../../recording/effects/directives';
import { speak, cancelSpeech } from '../../recording/effects/speech';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseEmail } from './parseEmail';
import type { Email as EmailDoc, Field } from './parseEmail';
import { DEFAULT_EMAIL_SETTINGS, emailFocus } from './settings';
import type { EmailSettings } from './settings';
import { EmailReel } from './EmailReel';
import { EmailDesktop } from './EmailDesktop';
import './email.css';

export type SendState = 'idle' | 'sending' | 'sent';
export type Fields = Record<Field, string>;

const EMPTY: Fields = { to: '', cc: '', subject: '', body: '' };

/* The whole arc of writing and sending one email. The typewriter fills To / Cc /
   Subject / Body in turn, then [[send]] fires the "Sending… → Sent" snackbar.
   The same plan drives two looks — a desktop Gmail (sidebar + inbox + compose
   popup) and a mobile reel — chosen by the Layout tab. */

export interface EmailView {
  doc: EmailDoc;
  s: EmailSettings;
  fields: Fields;
  active: Field | null;
  send: SendState;
  showSnackbar: boolean;
  preview: boolean;
  playing: boolean;
  caret: (f: Field) => boolean;
}

export function Email() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();
  const [s] = useSimSettings<EmailSettings>();
  const fxc = useEffects();

  const doc = useMemo(() => parseEmail(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  // resolver: turn named effect targets (field names) into coordinates so
  // [[zoom:body]] / [[cursor:send]] work against this layout.
  const resolve = {
    zoom: (name: string) => { const f = emailFocus(s, name as any); return { cx: f.cx, cy: f.cy, z: f.z }; },
    point: (name: string) => { const f = emailFocus(s, name as any); return { x: f.x, y: f.y }; },
  };

  const [fields, setFields] = useState<Fields>(EMPTY);
  const [active, setActive] = useState<Field | null>(null);
  const [send, setSend] = useState<SendState>('idle');
  const fieldsRef = useRef<Fields>(EMPTY);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    const us = rec.settings;
    const fxOpts = {
      notifSound: s.notifSound, notifMs: s.notifMs, notifAccent: s.accent,
      subtitles: us.subtitles,
      speak: us.narrate ? (text: string) => speak(text, { voice: us.ttsVoice, rate: us.ttsRate, pitch: us.ttsPitch }) : undefined,
    };

    steps.push({
      kind: 'reveal',
      fn: () => {
        fieldsRef.current = EMPTY;
        setFields(EMPTY);
        setActive(null);
        setSend('idle');
        cancelSpeech();
        fxc.resetAll();
      },
    });

    for (const op of doc.ops) {
      switch (op.kind) {
        case 'type': {
          let base = '';
          steps.push({
            kind: 'reveal',
            fn: () => {
              base = fieldsRef.current[op.field];
              setActive(op.field);
              // camera follows the active field (zoom persists between fields);
              // optional fake cursor taps into it.
              if (s.camFollow) { const f = emailFocus(s, op.field); fxc.setCamera({ cx: f.cx, cy: f.cy, z: f.z }); }
              if (s.cursor) { const f = emailFocus(s, op.field); fxc.cursorTo(f.x, f.y); fxc.cursorClick(); }
            },
            delay: s.cursor ? 360 : 0,
          });
          steps.push({
            kind: 'type',
            text: op.text,
            onUpdate: (v) => {
              const next = { ...fieldsRef.current, [op.field]: base + v };
              fieldsRef.current = next;
              setFields(next);
            },
          });
          break;
        }
        case 'pause':
          steps.push({ kind: 'pause', ms: op.ms });
          break;
        case 'fx':
          steps.push({ kind: 'reveal', fn: () => runEffectOp(fxc, op.op, resolve, fxOpts) });
          break;
        case 'send':
          steps.push({
            kind: 'reveal',
            fn: () => {
              setActive(null);
              setSend('sending');
              if (s.cursor) { const f = emailFocus(s, 'send'); fxc.cursorTo(f.x, f.y); fxc.cursorClick(); }
              if (s.camFollow && !s.camPersist) fxc.setCamera(null);
            },
            delay: 1000,
          });
          steps.push({ kind: 'reveal', fn: () => setSend('sent'), delay: 1600 });
          break;
      }
    }

    steps.push({
      kind: 'reveal',
      fn: () => { setActive(null); if (s.camFollow && !s.camPersist) fxc.setCamera(null); },
    });
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

  const shown: Fields = preview ? staticFields(doc) : fields;
  const view: EmailView = {
    doc,
    s,
    fields: shown,
    active,
    send,
    showSnackbar: preview ? false : send !== 'idle',
    preview,
    playing: rec.playing,
    caret: (f: Field) => !preview && rec.playing && active === f,
  };

  return s.layout === 'desktop' ? <EmailDesktop v={view} /> : <EmailReel v={view} />;
}

export function initials(name: string): string {
  return name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || 'Y';
}

/** The whole email in its final state, for the static edit-mode preview. */
function staticFields(doc: EmailDoc): Fields {
  const out: Fields = { to: '', cc: '', subject: '', body: '' };
  for (const op of doc.ops) {
    if (op.kind === 'type') out[op.field] += settledText(op.text);
  }
  return out;
}

export { DEFAULT_EMAIL_SETTINGS };
