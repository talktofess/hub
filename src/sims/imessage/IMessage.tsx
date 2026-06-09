import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseChat } from './parseChat';
import type { Chat, ChatOp } from './parseChat';
import './imessage.css';

interface Bubble {
  side: 'sent' | 'recv';
  text: string;
  reaction?: string;
  status?: 'Delivered' | 'Read';
}

export function IMessage() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const chat = useMemo(() => parseChat(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [composing, setComposing] = useState('');
  const [composerActive, setComposerActive] = useState(false);
  const [theirTyping, setTheirTyping] = useState(false);
  const composingRef = useRef('');
  const scrollRef = useRef<HTMLDivElement>(null);

  // keep the latest message in view as the thread grows
  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [bubbles, theirTyping, composing]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({
      kind: 'reveal',
      fn: () => {
        setBubbles([]);
        setComposing('');
        composingRef.current = '';
        setComposerActive(false);
        setTheirTyping(false);
      },
    });

    for (const op of chat.ops) {
      switch (op.kind) {
        case 'type':
          steps.push({
            kind: 'type',
            text: op.text,
            onUpdate: (v) => {
              composingRef.current = v;
              setComposing(v);
              setComposerActive(true);
            },
          });
          break;
        case 'send':
          steps.push({
            kind: 'reveal',
            fn: () => {
              const text = composingRef.current.trim();
              composingRef.current = '';
              setComposing('');
              setComposerActive(false);
              if (text) setBubbles((b) => [...b, { side: 'sent', text }]);
            },
            delay: 260,
          });
          break;
        case 'delivered':
          steps.push({ kind: 'reveal', fn: () => setLastStatus(setBubbles, 'sent', 'Delivered') });
          break;
        case 'read':
          steps.push({ kind: 'reveal', fn: () => setLastStatus(setBubbles, 'sent', 'Read') });
          break;
        case 'pause':
          steps.push({ kind: 'pause', ms: op.ms });
          break;
        case 'typing':
          steps.push({ kind: 'reveal', fn: () => setTheirTyping(true), delay: op.ms });
          steps.push({ kind: 'reveal', fn: () => setTheirTyping(false), delay: 200 });
          break;
        case 'recv':
          steps.push({
            kind: 'reveal',
            fn: () => setBubbles((b) => [...b, { side: 'recv', text: settledText(op.text) }]),
            delay: 320,
          });
          break;
        case 'react':
          steps.push({ kind: 'reveal', fn: () => reactLast(setBubbles, op.emoji), delay: 240 });
          break;
      }
    }

    steps.push({ kind: 'reveal', fn: () => setComposerActive(false) });
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

  // In edit preview (not playing), show the whole conversation settled.
  const shown = preview ? staticBubbles(chat) : bubbles;
  const composerText = preview ? '' : composing;
  const showTheirTyping = preview ? false : theirTyping;

  return (
    <div className="im-root">
      <StatusBar />
      <Header name={chat.name} />
      <div className="im-thread" ref={scrollRef}>
        <div className="im-thread-inner">
          {shown.map((b, i) => (
            <BubbleRow key={i} bubble={b} />
          ))}
          {showTheirTyping && (
            <div className="im-row recv">
              <div className="im-bubble recv typing">
                <span className="dot" /><span className="dot" /><span className="dot" />
              </div>
            </div>
          )}
        </div>
      </div>
      <Composer text={composerText} caret={!preview && rec.playing && composerActive} />
    </div>
  );
}

function BubbleRow({ bubble }: { bubble: Bubble }) {
  return (
    <div className={`im-row ${bubble.side}`}>
      <div className="im-stack">
        <div className={`im-bubble ${bubble.side}`}>
          {bubble.text}
          {bubble.reaction && <span className="im-tapback">{bubble.reaction}</span>}
        </div>
        {bubble.status && <div className="im-status">{bubble.status}</div>}
      </div>
    </div>
  );
}

function StatusBar() {
  return (
    <div className="im-statusbar">
      <span className="im-time">9:41</span>
      <span className="im-status-right">
        <span className="im-signal"><i /><i /><i /><i /></span>
        <span className="im-5g">5G</span>
        <span className="im-battery"><span className="im-battery-fill" /></span>
      </span>
    </div>
  );
}

function Header({ name }: { name: string }) {
  const initials = name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || 'A';
  return (
    <div className="im-header">
      <span className="im-chevron">‹</span>
      <div className="im-contact">
        <div className="im-avatar">{initials}</div>
        <div className="im-name">{name} <span className="im-name-chevron">›</span></div>
      </div>
      <span className="im-facetime">
        <svg viewBox="0 0 28 18" width="56" height="36" aria-hidden>
          <rect x="1" y="2" width="18" height="14" rx="4" fill="#007AFF" />
          <path d="M21 7 L26 3.5 V14.5 L21 11 Z" fill="#007AFF" />
        </svg>
      </span>
    </div>
  );
}

function Composer({ text, caret }: { text: string; caret: boolean }) {
  const empty = text.length === 0 && !caret;
  return (
    <div className="im-composer">
      <span className="im-plus">+</span>
      <div className={`im-input ${empty ? 'empty' : ''}`}>
        {empty ? <span className="im-input-ph">iMessage</span> : (
          <span className="im-input-text">{text}{caret && <span className="caret" />}</span>
        )}
        {!empty && <span className="im-send">↑</span>}
      </div>
    </div>
  );
}

// --- helpers for reveal closures -------------------------------------------

function setLastStatus(
  set: React.Dispatch<React.SetStateAction<Bubble[]>>,
  side: Bubble['side'],
  status: Bubble['status'],
) {
  set((b) => {
    const next = b.slice();
    for (let i = next.length - 1; i >= 0; i--) {
      if (next[i].side === side) {
        next[i] = { ...next[i], status };
        break;
      }
    }
    return next;
  });
}

function reactLast(set: React.Dispatch<React.SetStateAction<Bubble[]>>, emoji: string) {
  set((b) => {
    if (b.length === 0) return b;
    const next = b.slice();
    next[next.length - 1] = { ...next[next.length - 1], reaction: emoji };
    return next;
  });
}

/** The whole conversation in its final state, for the static edit-mode preview. */
function staticBubbles(chat: Chat): Bubble[] {
  const out: Bubble[] = [];
  let pending = '';
  const push = (b: Bubble) => out.push(b);
  for (const op of chat.ops as ChatOp[]) {
    switch (op.kind) {
      case 'type':
        pending += settledText(op.text);
        break;
      case 'send':
        if (pending.trim()) push({ side: 'sent', text: pending.trim() });
        pending = '';
        break;
      case 'recv':
        push({ side: 'recv', text: settledText(op.text) });
        break;
      case 'delivered':
        if (out.length) setStatusOf(out, 'sent', 'Delivered');
        break;
      case 'read':
        if (out.length) setStatusOf(out, 'sent', 'Read');
        break;
      case 'react':
        if (out.length) out[out.length - 1].reaction = op.emoji;
        break;
      default:
        break;
    }
  }
  return out;
}

function setStatusOf(out: Bubble[], side: Bubble['side'], status: Bubble['status']) {
  for (let i = out.length - 1; i >= 0; i--) {
    if (out[i].side === side) {
      out[i].status = status;
      return;
    }
  }
}
