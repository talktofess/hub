import { useEffect, useRef } from 'react';
import type { EmailView } from './Email';
import { initials } from './Email';
import type { InboxRow } from './settings';

/* Desktop Gmail at 1920×1080 — the full web app: search chrome, a left rail
   with an unread Inbox badge, the inbox list, and a compose popup docked
   bottom-right that the typewriter fills. */
export function EmailDesktop({ v }: { v: EmailView }) {
  const { doc, s, fields, send } = v;
  const unread = s.inbox.filter((r) => r.unread).length;

  return (
    <div className="gd-root" data-theme={s.theme} style={{ ['--gm-accent' as any]: s.accent, zoom: s.density }}>
      <TopBar account={doc.account} from={doc.from} />
      <div className="gd-main">
        {s.sidebar && <Sidebar unread={unread} accent={s.accent} />}
        <Inbox rows={s.inbox} />
      </div>
      <Compose v={v} pos={s.composePos} sending={send !== 'idle'} state={send} />
    </div>
  );
}

function TopBar({ account, from }: { account: string; from: string }) {
  return (
    <div className="gd-topbar">
      <span className="gd-burger">≡</span>
      <span className="gd-logo">
        <svg viewBox="0 0 24 24" width="40" height="40" aria-hidden>
          <path fill="#EA4335" d="M2 6.5 12 14 22 6.5V18a2 2 0 0 1-2 2h-1V9.2l-7 5.2-7-5.2V20H4a2 2 0 0 1-2-2V6.5Z"/>
          <path fill="#4285F4" d="M22 5v1.5L12 14 2 6.5V5a2 2 0 0 1 2-2h.5L12 8.7 19.5 3H20a2 2 0 0 1 2 2Z"/>
        </svg>
        <span className="gd-logo-word">Gmail</span>
      </span>
      <div className="gd-search">
        <span className="gd-search-ic">🔍</span>
        <span className="gd-search-ph">Search mail</span>
      </div>
      <span className="gd-spacer" />
      <span className="gd-topic" title={account}>{from ? initials(from) : 'Y'}</span>
    </div>
  );
}

function Sidebar({ unread, accent }: { unread: number; accent: string }) {
  const items: { ic: string; label: string; badge?: number; active?: boolean }[] = [
    { ic: '📥', label: 'Inbox', badge: unread, active: true },
    { ic: '★', label: 'Starred' },
    { ic: '🕘', label: 'Snoozed' },
    { ic: '➤', label: 'Sent' },
    { ic: '📄', label: 'Drafts' },
  ];
  return (
    <aside className="gd-side">
      <button className="gd-compose-btn" style={{ background: 'color-mix(in srgb, ' + accent + ' 14%, #fff)' }}>
        <span className="gd-pencil">✎</span> Compose
      </button>
      <nav className="gd-nav">
        {items.map((it) => (
          <div key={it.label} className={'gd-nav-item' + (it.active ? ' active' : '')}>
            <span className="gd-nav-ic">{it.ic}</span>
            <span className="gd-nav-label">{it.label}</span>
            {it.badge ? <span className="gd-nav-badge">{it.badge}</span> : null}
          </div>
        ))}
      </nav>
    </aside>
  );
}

function Inbox({ rows }: { rows: InboxRow[] }) {
  return (
    <main className="gd-inbox">
      <div className="gd-inbox-toolbar">
        <span className="gd-check-all">▢</span>
        <span className="gd-tool">⟳</span>
        <span className="gd-tool">⋮</span>
        <span className="gd-spacer" />
        <span className="gd-count">1–{rows.length} of {Math.max(rows.length, 1) * 37}</span>
        <span className="gd-tool">‹</span>
        <span className="gd-tool">›</span>
      </div>
      <div className="gd-tabs">
        <div className="gd-tab active">Primary</div>
        <div className="gd-tab">Promotions</div>
        <div className="gd-tab">Social</div>
      </div>
      <div className="gd-rows">
        {rows.map((r, i) => (
          <div key={i} className={'gd-row' + (r.unread ? ' unread' : '')}>
            <span className="gd-row-check">▢</span>
            <span className="gd-row-star">☆</span>
            <span className="gd-row-from">{r.from}</span>
            <span className="gd-row-text">
              <span className="gd-row-subj">{r.subject}</span>
              <span className="gd-row-snip"> — {r.snippet}</span>
            </span>
            <span className="gd-row-time">{r.time}</span>
          </div>
        ))}
      </div>
    </main>
  );
}

function Compose({ v, pos, sending, state }: { v: EmailView; pos: 'br' | 'center'; sending: boolean; state: 'idle' | 'sending' | 'sent' }) {
  const { fields, s } = v;
  const bodyRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = bodyRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [fields, v.active]);

  return (
    <div className={'gd-compose ' + pos}>
      <div className="gd-compose-head">
        <span>New Message</span>
        <span className="gd-compose-controls">— ⤢ ✕</span>
      </div>
      <CRow label="To" value={fields.to} caret={v.caret('to')} />
      {(s.showCc || fields.cc) && <CRow label="Cc" value={fields.cc} caret={v.caret('cc')} />}
      <CRow label="Subject" value={fields.subject} caret={v.caret('subject')} subject />
      <div className="gd-compose-body" ref={bodyRef}>
        <span>{fields.body}</span>
        {v.caret('body') && <span className="caret" />}
        {fields.body.length === 0 && !v.caret('body') && <span className="gd-compose-ph">Compose email</span>}
      </div>
      <div className="gd-compose-foot">
        <button className={'gd-send' + (sending ? ' is-sending' : '')}>
          {state === 'sent' ? 'Sent ✓' : state === 'sending' ? 'Sending…' : 'Send'}
        </button>
        <span className="gd-foot-ics">A 📎 🙂 🖼 🔒</span>
        <span className="gd-spacer" />
        <span className="gd-trash">🗑</span>
      </div>
    </div>
  );
}

function CRow({ label, value, caret, subject }: { label: string; value: string; caret: boolean; subject?: boolean }) {
  const empty = value.length === 0 && !caret;
  return (
    <div className={'gd-crow' + (subject ? ' subject' : '')}>
      {!subject && <span className="gd-crow-label">{label}</span>}
      <span className={'gd-crow-val' + (empty ? ' empty' : '')}>
        {empty ? (subject ? 'Subject' : '') : value}
        {caret && <span className="caret" />}
      </span>
    </div>
  );
}
