import { useEffect, useMemo, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { useTypewriter } from '../../recording/useTypewriter';
import { settledText } from '../../recording/typer';
import type { TypeStep } from '../../recording/typer';
import { parseTiktok } from './parseTiktok';
import type { Place } from './parseTiktok';
import './tiktok.css';

/* A TikTok-style vertical with a hook banner, @handle, an action rail, and a
   caption that retypes itself as it hops between positions (top / center /
   bottom) — the classic comparison / "this vs that" format. The background is
   whatever media is set in the shell (or a flat gradient). */
export function Tiktok() {
  const rec = useRecording();
  const { run, stop } = useTypewriter();

  const doc = useMemo(() => parseTiktok(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [place, setPlace] = useState<Place>('center');
  const [tags, setTags] = useState<string[]>([]);
  const [caption, setCaption] = useState('');

  function buildPlan(): TypeStep[] {
    const steps: TypeStep[] = [];
    steps.push({ kind: 'reveal', fn: () => { setCaption(''); setTags([]); setPlace('center'); } });
    for (const op of doc.ops) {
      if (op.kind === 'pause') { steps.push({ kind: 'pause', ms: op.ms }); continue; }
      const seg = op.seg;
      steps.push({
        kind: 'reveal',
        fn: () => { setPlace(seg.place); setTags(seg.tags); setCaption(''); },
        delay: 260,
      });
      steps.push({ kind: 'type', text: seg.text, onUpdate: (v) => setCaption(v) });
      steps.push({ kind: 'pause', ms: 320 });
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

  // In edit preview, show the last segment settled in its place.
  const lastSeg = useMemo(() => {
    const segs = doc.ops.filter((o) => o.kind === 'segment');
    return segs.length ? (segs[segs.length - 1] as { seg: { place: Place; tags: string[]; text: string } }).seg : null;
  }, [doc]);

  const shownPlace = preview ? (lastSeg?.place ?? 'center') : place;
  const shownTags = preview ? (lastSeg?.tags ?? []) : tags;
  const shownCaption = preview ? settledText(lastSeg?.text ?? '') : caption;
  const showCaret = !preview && rec.playing;

  return (
    <div className="tk-root">
      {doc.hook && <div className="tk-hook">{doc.hook}</div>}

      <div className={`tk-caption-zone place-${shownPlace}`}>
        {(shownCaption || showCaret) && (
          <div className="tk-caption">
            <span className="tk-caption-text">{shownCaption}{showCaret && <span className="caret" />}</span>
          </div>
        )}
      </div>

      <Rail />

      <div className="tk-footer">
        <div className="tk-handle">{doc.handle}</div>
        {doc.sub && <div className="tk-sub">{doc.sub}</div>}
        {shownTags.length > 0 && (
          <div className="tk-tags">{shownTags.map((t, i) => <span key={i} className="tk-tag">#{t}</span>)}</div>
        )}
      </div>
    </div>
  );
}

function Rail() {
  return (
    <div className="tk-rail">
      <div className="tk-avatar" />
      <RailBtn glyph="♥" count="128.4k" />
      <RailBtn glyph="💬" count="2,041" />
      <RailBtn glyph="↪" count="Share" />
      <div className="tk-disc" />
    </div>
  );
}

function RailBtn({ glyph, count }: { glyph: string; count: string }) {
  return (
    <div className="tk-railbtn">
      <span className="tk-railglyph">{glyph}</span>
      <span className="tk-railcount">{count}</span>
    </div>
  );
}
