import { useEffect, useRef } from 'react';
import type { EmailView } from './Email';
import { initials } from './Email';
import type { Field } from './parseEmail';

/* The mobile reel — a Gmail compose sheet at 1080×1920. Vertical, full-bleed,
   the body is the reading zone where the typed message lands. */
export function EmailReel({ v }: { v: EmailView }) {
  const { doc, s, fields, send, showSnackbar } = v;
  const bodyRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = bodyRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [fields, v.active]);

  return (
    <div className="gm-root" data-theme={s.theme} style={{ ['--gm-accent' as any]: s.accent }}>
      <StatusBar />
      <AppBar sending={send !== 'idle'} />

      <div className="gm-fields">
        <div className="gm-from">
          <div className="gm-label">From</div>
          <div className="gm-from-value">{doc.account}</div>
          <div className="gm-avatar">{initials(doc.from)}</div>
        </div>
        <FieldRow label="To" value={fields.to} caret={v.caret('to')} />
        {(s.showCc || fields.cc) && <FieldRow label="Cc" value={fields.cc} caret={v.caret('cc')} />}
        <FieldRow label="Subject" value={fields.subject} caret={v.caret('subject')} bold />
      </div>

      <div className="gm-body" ref={bodyRef}>
        <span className="gm-body-text">{fields.body}</span>
        {v.caret('body') && <span className="caret" />}
        {fields.body.length === 0 && !v.caret('body') && <span className="gm-body-ph">Compose email</span>}
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

function Snackbar({ show, state }: { show: boolean; state: 'idle' | 'sending' | 'sent' }) {
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

export type { Field };
