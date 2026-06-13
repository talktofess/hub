import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseLists } from './parseLists';
import type { ListItem } from './parseLists';
import './lists.css';

interface Shown { meta: ListItem; title: string; text: string }

/* A ranked countdown: the list title types in, then each item card drops in and
   types its heading + blurb, with rank / score / tier / badge chips. */
export function Lists() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const doc = useMemo(() => parseLists(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';
  const items = useMemo(() => doc.ops.filter((o) => o.kind === 'item').map((o) => (o as { item: ListItem }).item), [doc]);

  const [title, setTitle] = useState('');
  const [cards, setCards] = useState<Shown[]>([]);
  const [active, setActive] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [cards, active, title]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { setTitle(''); setCards([]); setActive(null); } });
    if (doc.title) {
      steps.push({ kind: 'type', text: doc.title, onUpdate: (v) => { setActive('title'); setTitle(v); } });
      steps.push({ kind: 'pause', ms: 360 });
    }

    let i = 0;
    for (const op of doc.ops) {
      if (op.kind === 'pause') { steps.push({ kind: 'pause', ms: op.ms }); continue; }
      const idx = i++;
      const meta = op.item;
      steps.push({
        kind: 'reveal',
        fn: () => { setActive(`${idx}:title`); setCards((c) => [...c, { meta, title: '', text: '' }]); },
        delay: 320,
      });
      steps.push({
        kind: 'type',
        text: meta.title,
        onUpdate: (v) => { setActive(`${idx}:title`); setCards((c) => c.map((x, k) => (k === idx ? { ...x, title: v } : x))); },
      });
      steps.push({ kind: 'pause', ms: 160 });
      steps.push({
        kind: 'type',
        text: meta.text,
        onUpdate: (v) => { setActive(`${idx}:text`); setCards((c) => c.map((x, k) => (k === idx ? { ...x, text: v } : x))); },
      });
      steps.push({ kind: 'pause', ms: 280 });
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

  const shownTitle = preview ? settledText(doc.title) : title;
  const shownCards: Shown[] = preview
    ? items.map((m) => ({ meta: m, title: settledText(m.title), text: settledText(m.text) }))
    : cards;
  const caret = (key: string) => !preview && rec.playing && active === key;

  return (
    <div className="ls-root">
      <div className="ls-scroll" ref={scrollRef}>
        {(shownTitle || caret('title')) && (
          <h1 className="ls-title">{shownTitle}{caret('title') && <span className="caret" />}</h1>
        )}
        <div className="ls-col">
          {shownCards.map((c, i) => (
            <div className="ls-card" key={i}>
              {c.meta.rank && <div className="ls-rank">{c.meta.rank}</div>}
              <div className="ls-body">
                <div className="ls-card-head">
                  <span className="ls-card-title">{c.title}{caret(`${i}:title`) && <span className="caret" />}</span>
                  {c.meta.badge && <span className="ls-badge">{c.meta.badge}</span>}
                </div>
                <div className="ls-card-text">{c.text}{caret(`${i}:text`) && <span className="caret" />}</div>
                {(c.meta.score || c.meta.tier) && (
                  <div className="ls-chips">
                    {c.meta.tier && <span className={`ls-tier tier-${c.meta.tier.toLowerCase()}`}>{c.meta.tier}</span>}
                    {c.meta.score && <span className="ls-score">{c.meta.score}</span>}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
