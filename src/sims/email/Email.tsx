import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseEmail } from './parseEmail';
import type { Email as EmailDoc, Field } from './parseEmail';
import './email.css';

type SendState = 'idle' | 'sending' | 'sent';
type Fields = Record<Field, string>;

const EMPTY: Fields = { to: '', subject: '', body: '' };

/* A Gmail mobile compose screen at 1080x1920. The typewriter fills To / Subject
   / Body in turn, then [[send]] fires the "Sending… → Sent" snackbar — the whole
   arc of writing and sending one email, captured as a vertical reel. */
export function Email() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const doc = useMemo(() => parseEmail(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [fields, setFields] = useState<Fields>(EMPTY);
  const [active, setActive] = useState<Field | null>(null);
  const [send, setSend] = useState<SendState>('idle');
  const fieldsRef = useRef<Fields>(EMPTY);
  const bodyRef = useRef<HTMLDivElement>(null);

  // keep the latest body text in view as it grows past the fold
  useEffect(() => {
    const el = bodyRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [fields, active]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({
      kind: 'reveal',
      fn: () => {
        fieldsRef.current = EMPTY;
        setFields(EMPTY);
        setActive(null);
        setSend('idle');
      },
    });

    for (const op of doc.ops) {
      switch (op.kind) {
        case 'type': {
          // capture whatever is already in the field so multiple segments
          // (e.g. a [[pause]] mid-body) append instead of overwrite
          let base = '';
          steps.push({
            kind: 'reveal',
            fn: () => { base = fieldsRef.current[op.field]; setActive(op.field); },
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
        case 'send':
          steps.push({ kind: 'reveal', fn: () => { setActive(null); setSend('sending'); }, delay: 1000 });
          steps.push({ kind: 'reveal', fn: () => setSend('sent'), delay: 1600 });
          break;
      }
    }

    steps.push({ kind: 'reveal', fn: () => setActive(null) });
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

  // In edit preview, show the whole email settled in its fields.
  const shown: Fields = preview ? staticFields(doc) : fields;
  const showSnackbar = preview ? false : send !== 'idle';

  const caret = (f: Field) => !preview && rec.playing && active === f;

  return (
    <div className="gm-root">
      <StatusBar />
      <AppBar sending={send !== 'idle'} />

      <div className="gm-fields">
        <div className="gm-from">
          <div className="gm-label">From</div>
          <div className="gm-from-value">{doc.account}</div>
          <div className="gm-avatar">{initials(doc.from)}</div>
        </div>
        <FieldRow label="To" value={shown.to} caret={caret('to')} />
        <FieldRow label="Subject" value={shown.subject} caret={caret('subject')} bold />
      </div>

      <div className="gm-body" ref={bodyRef}>
        <span className="gm-body-text">{shown.body}</span>
        {caret('body') && <span className="caret" />}
        {emptyBody(shown.body, caret('body')) && <span className="gm-body-ph">Compose email</span>}
      </div>

      <Snackbar show={showSnackbar} state={send} />
    </div>
  );
}

function FieldRow({ label, value, caret, bold }: { label: string; value: string; caret: boolean; bold?: boolean }) {
  const empty = value.length === 0 && !caret;
  return (
    <div className="gm-field">
      <div className="gm-label">{label}</div>
      <div className={`gm-value ${bold ? 'bold' : ''} ${empty ? 'empty' : ''}`}>
        {empty ? label : value}
        {caret && <span className="caret" />}
      </div>
    </div>
  );
}

function AppBar({ sending }: { sending: boolean }) {
  return (
    <div className="gm-appbar">
      <span className="gm-back">←</span>
      <span className="gm-spacer" />
      <span className="gm-action" aria-label="Attach">
        <svg viewBox="0 0 24 24" width="48" height="48"><path fill="currentColor" d="M16.5 6v11.5a4 4 0 1 1-8 0V5a2.5 2.5 0 0 1 5 0v10.5a1 1 0 1 1-2 0V6H10v9.5a2.5 2.5 0 0 0 5 0V5a4 4 0 1 0-8 0v12.5a5.5 5.5 0 0 0 11 0V6h-1.5Z"/></svg>
      </span>
      <span className={`gm-send ${sending ? 'is-sending' : ''}`} aria-label="Send">
        <svg viewBox="0 0 24 24" width="44" height="44"><path fill="currentColor" d="M3 20.5v-6l8-2.5-8-2.5v-6l19 8.5-19 8.5Z"/></svg>
      </span>
      <span className="gm-action gm-more" aria-label="More">⋮</span>
    </div>
  );
}

function StatusBar() {
  return (
    <div className="gm-statusbar">
      <span className="gm-time">9:41</span>
      <span className="gm-status-right">
        <span className="gm-signal"><i /><i /><i /><i /></span>
        <span className="gm-5g">5G</span>
        <span className="gm-battery"><span className="gm-battery-fill" /></span>
      </span>
    </div>
  );
}

function Snackbar({ show, state }: { show: boolean; state: SendState }) {
  return (
    <div className={`gm-snackbar ${show ? 'show' : ''}`}>
      {state === 'sending' ? (
        <><span className="gm-spinner" /><span>Sending…</span></>
      ) : (
        <><span className="gm-check">✓</span><span>Sent</span><span className="gm-undo">Undo</span></>
      )}
    </div>
  );
}

function emptyBody(body: string, caret: boolean): boolean {
  return body.length === 0 && !caret;
}

function initials(name: string): string {
  return name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || 'Y';
}

/** The whole email in its final state, for the static edit-mode preview. */
function staticFields(doc: EmailDoc): Fields {
  const out: Fields = { to: '', subject: '', body: '' };
  for (const op of doc.ops) {
    if (op.kind === 'type') out[op.field] += settledText(op.text);
  }
  return out;
}
