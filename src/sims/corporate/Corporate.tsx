import { useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseCorporate } from './parseCorporate';
import type { BlockTag, CorporateOp } from './parseCorporate';
import './corporate.css';

interface Block { tag: BlockTag; text: string; attr?: string }

/* A clean editorial column that types itself out — title, headings, paragraphs
   and pull-quotes appear in order while the page scrolls to follow the pen. */
export function Corporate() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const ops = useMemo(() => parseCorporate(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [blocks, setBlocks] = useState<Block[]>([]);
  const [active, setActive] = useState(-1);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [blocks, active]);

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { setBlocks([]); setActive(-1); } });
    let idx = 0;
    for (const op of ops) {
      if (op.kind === 'pause') { steps.push({ kind: 'pause', ms: op.ms }); continue; }
      const myIdx = idx++;
      const b = op;
      steps.push({
        kind: 'reveal',
        fn: () => { setActive(myIdx); setBlocks((bs) => [...bs, { tag: b.tag, text: '', attr: b.attr }]); },
        delay: b.tag === 'para' ? 120 : 280,
      });
      steps.push({
        kind: 'type',
        text: b.text,
        onUpdate: (v) => setBlocks((bs) => bs.map((x, i) => (i === myIdx ? { ...x, text: v } : x))),
      });
      steps.push({ kind: 'pause', ms: b.tag === 'para' ? 220 : 120 });
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

  const shown: Block[] = preview
    ? ops.filter((o): o is Extract<CorporateOp, { kind: 'block' }> => o.kind === 'block')
        .map((o) => ({ tag: o.tag, text: settledText(o.text), attr: o.attr }))
    : blocks;

  return (
    <div className="co-root">
      <div className="co-page" ref={scrollRef}>
        <article className="co-col">
          {shown.map((b, i) => (
            <BlockView key={i} block={b} caret={!preview && rec.playing && active === i} />
          ))}
        </article>
      </div>
    </div>
  );
}

function BlockView({ block, caret }: { block: Block; caret: boolean }) {
  const c = caret ? <span className="caret" /> : null;
  switch (block.tag) {
    case 'title': return <h1 className="co-title">{block.text}{c}</h1>;
    case 'subtitle': return <div className="co-subtitle">{block.text}{c}</div>;
    case 'byline': return <div className="co-byline">{block.text}{c}</div>;
    case 'h2': return <h2 className="co-h2">{block.text}{c}</h2>;
    case 'h3': return <h3 className="co-h3">{block.text}{c}</h3>;
    case 'quote':
      return (
        <blockquote className="co-quote">
          <span className="co-quote-text">{block.text}{c}</span>
          {block.attr && <cite className="co-quote-attr">— {block.attr}</cite>}
        </blockquote>
      );
    default: return <p className="co-para">{block.text}{c}</p>;
  }
}
