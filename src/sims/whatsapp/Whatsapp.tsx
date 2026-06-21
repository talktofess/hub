import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { useSimSettings } from '../../recording/useSimSettings';
import { useEffects } from '../../recording/effects/EffectsProvider';
import { runEffectOp } from '../../recording/effects/directives';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseWhatsapp } from './parseWhatsapp';
import type { Quote, WaChat, WaOp } from './parseWhatsapp';
import './whatsapp.css';

export interface WaSettings { theme: 'light' | 'dark'; wallpaper: boolean }
export const DEFAULT_WA_SETTINGS: WaSettings = { theme: 'dark', wallpaper: true };

interface Bubble {
  side: 'sent' | 'recv';
  text: string;
  status?: 'sent' | 'delivered' | 'read';
  reaction?: string;
  quote?: Quote;
}

export function Whatsapp() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();
  const fxc = useEffects();
  const [s] = useSimSettings<WaSettings>();

  const chat = useMemo(() => parseWhatsapp(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [composing, setComposing] = useState('');
  const [composerActive, setComposerActive] = useState(false);
  const [theirTyping, setTheirTyping] = useState(false);
  const composingRef = useRef('');
  const quoteRef = useRef<{ quote: Quote; side: 'sent' | 'recv' } | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [bubbles, theirTyping, composing]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({
      kind: 'reveal',
      fn: () => {
        setBubbles([]); setComposing(''); composingRef.current = '';
        setComposerActive(false); setTheirTyping(false); quoteRef.current = null;
        fxc.resetAll();
      },
    });

    for (const op of chat.ops) {
      switch (op.kind) {
        case 'type':
          steps.push({
            kind: 'type', text: op.text,
            onUpdate: (v) => { composingRef.current = v; setComposing(v); setComposerActive(true); },
          });
          break;
        case 'send':
          steps.push({
            kind: 'reveal', delay: 240,
            fn: () => {
              const text = composingRef.current.trim();
              composingRef.current = ''; setComposing(''); setComposerActive(false);
              const q = quoteRef.current?.side === 'sent' ? quoteRef.current.quote : undefined;
              if (quoteRef.current?.side === 'sent') quoteRef.current = null;
              if (text) setBubbles((b) => [...b, { side: 'sent', text, status: 'sent', quote: q }]);
            },
          });
          break;
        case 'recv':
          steps.push({
            kind: 'reveal', delay: 320,
            fn: () => {
              const q = quoteRef.current?.side === 'recv' ? quoteRef.current.quote : undefined;
              if (quoteRef.current?.side === 'recv') quoteRef.current = null;
              setBubbles((b) => [...b, { side: 'recv', text: settledText(op.text), quote: q }]);
            },
          });
          break;
        case 'story':
          steps.push({ kind: 'reveal', fn: () => { quoteRef.current = { quote: op.quote, side: op.side }; } });
          break;
        case 'delivered':
          steps.push({ kind: 'reveal', fn: () => setLastStatus(setBubbles, 'delivered') });
          break;
        case 'read':
          steps.push({ kind: 'reveal', fn: () => setLastStatus(setBubbles, 'read') });
          break;
        case 'typing':
          steps.push({ kind: 'reveal', fn: () => setTheirTyping(true), delay: op.ms });
          steps.push({ kind: 'reveal', fn: () => setTheirTyping(false), delay: 180 });
          break;
        case 'pause':
          steps.push({ kind: 'pause', ms: op.ms });
          break;
        case 'react':
          steps.push({ kind: 'reveal', fn: () => reactLast(setBubbles, op.emoji), delay: 220 });
          break;
        case 'fx':
          steps.push({ kind: 'reveal', fn: () => runEffectOp(fxc, op.op) });
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

  const shown = preview ? staticBubbles(chat) : bubbles;
  const composerText = preview ? '' : composing;
  const showTheirTyping = preview ? false : theirTyping;

  return (
    <div className={'wa-root' + (s.wallpaper ? ' wall' : '')} data-theme={s.theme}>
      <StatusBar />
      <Header name={chat.name} typing={showTheirTyping} />
      <div className="wa-thread" ref={scrollRef}>
        <div className="wa-thread-inner">
          <div className="wa-daystamp"><span>TODAY</span></div>
          {shown.map((b, i) => <BubbleRow key={i} bubble={b} name={chat.name} />)}
          {showTheirTyping && (
            <div className="wa-row recv">
              <div className="wa-bubble recv typing"><span className="dot" /><span className="dot" /><span className="dot" /></div>
            </div>
          )}
        </div>
      </div>
      <Composer text={composerText} caret={!preview && rec.playing && composerActive} />
    </div>
  );
}

function BubbleRow({ bubble, name }: { bubble: Bubble; name: string }) {
  return (
    <div className={`wa-row ${bubble.side}`}>
      <div className={`wa-bubble ${bubble.side}`}>
        {bubble.quote && <QuoteBar quote={bubble.quote} name={name} />}
        <span className="wa-text">{bubble.text}</span>
        <span className="wa-meta">
          <span className="wa-time">9:41</span>
          {bubble.side === 'sent' && <Ticks status={bubble.status} />}
        </span>
        {bubble.reaction && <span className="wa-reaction">{bubble.reaction}</span>}
      </div>
    </div>
  );
}

function QuoteBar({ quote, name }: { quote: Quote; name: string }) {
  const who = quote.mine ? 'You' : quote.author || name;
  return (
    <div className="wa-quote">
      <div className="wa-quote-body">
        <span className="wa-quote-who">↩ {who}'s status</span>
        <span className="wa-quote-cap">{quote.caption || 'Status update'}</span>
      </div>
      <div className="wa-quote-thumb" />
    </div>
  );
}

function Ticks({ status }: { status?: Bubble['status'] }) {
  if (!status || status === 'sent') return <span className="wa-ticks">✓</span>;
  return <span className={'wa-ticks double' + (status === 'read' ? ' read' : '')}>✓✓</span>;
}

function StatusBar() {
  return (
    <div className="wa-statusbar">
      <span className="wa-time-sb">9:41</span>
      <span className="wa-sb-right">
        <span className="wa-signal"><i /><i /><i /><i /></span>
        <span className="wa-5g">5G</span>
        <span className="wa-battery"><span className="wa-battery-fill" /></span>
      </span>
    </div>
  );
}

function Header({ name, typing }: { name: string; typing: boolean }) {
  const initials = name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || 'M';
  return (
    <div className="wa-header">
      <span className="wa-chevron">‹</span>
      <div className="wa-avatar">{initials}</div>
      <div className="wa-contact">
        <div className="wa-name">{name}</div>
        <div className="wa-presence">{typing ? 'typing…' : 'online'}</div>
      </div>
      <span className="wa-hicons">
        <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor"><path d="M17 10.5V7a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-3.5l4 4v-11l-4 4Z"/></svg>
        <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor"><path d="M6.6 10.8a15 15 0 0 0 6.6 6.6l2.2-2.2a1 1 0 0 1 1-.25 11 11 0 0 0 3.4.55 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.3a1 1 0 0 1 1 1 11 11 0 0 0 .55 3.4 1 1 0 0 1-.25 1l-2.2 2.2Z"/></svg>
      </span>
    </div>
  );
}

function Composer({ text, caret }: { text: string; caret: boolean }) {
  const empty = text.length === 0 && !caret;
  return (
    <div className="wa-composer">
      <div className={`wa-input ${empty ? 'empty' : ''}`}>
        <span className="wa-emoji">🙂</span>
        {empty ? <span className="wa-input-ph">Message</span> : <span className="wa-input-text">{text}{caret && <span className="caret" />}</span>}
        <span className="wa-clip">📎</span>
        <span className="wa-cam">📷</span>
      </div>
      <span className="wa-mic">{empty ? '🎤' : '➤'}</span>
    </div>
  );
}

// --- reveal helpers ---------------------------------------------------------

function setLastStatus(set: React.Dispatch<React.SetStateAction<Bubble[]>>, status: NonNullable<Bubble['status']>) {
  set((b) => {
    const next = b.slice();
    for (let i = next.length - 1; i >= 0; i--) {
      if (next[i].side === 'sent') { next[i] = { ...next[i], status }; break; }
    }
    return next;
  });
}

function reactLast(set: React.Dispatch<React.SetStateAction<Bubble[]>>, emoji: string) {
  set((b) => {
    if (!b.length) return b;
    const next = b.slice();
    next[next.length - 1] = { ...next[next.length - 1], reaction: emoji };
    return next;
  });
}

function staticBubbles(chat: WaChat): Bubble[] {
  const out: Bubble[] = [];
  let pending = '';
  let quote: { quote: Quote; side: 'sent' | 'recv' } | null = null;
  for (const op of chat.ops as WaOp[]) {
    switch (op.kind) {
      case 'type': pending += settledText(op.text); break;
      case 'send':
        if (pending.trim()) out.push({ side: 'sent', text: pending.trim(), status: 'read', quote: quote?.side === 'sent' ? quote.quote : undefined });
        if (quote?.side === 'sent') quote = null;
        pending = '';
        break;
      case 'recv':
        out.push({ side: 'recv', text: settledText(op.text), quote: quote?.side === 'recv' ? quote.quote : undefined });
        if (quote?.side === 'recv') quote = null;
        break;
      case 'story': quote = { quote: op.quote, side: op.side }; break;
      case 'react': if (out.length) out[out.length - 1].reaction = op.emoji; break;
      default: break;
    }
  }
  return out;
}
