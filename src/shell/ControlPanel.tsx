import { useState } from 'react';
import { useRecording } from '../recording/useRecording';
import { getSim } from '../sims/registry';
import { buildObsUrl } from '../recording/url';

function guessKind(url: string): 'video' | 'audio' {
  return /\.(mp3|wav|m4a|aac|flac|oga|ogg)(\?|#|$)/i.test(url) ? 'audio' : 'video';
}

function copyText(t: string) {
  if (navigator.clipboard) { navigator.clipboard.writeText(t).catch(() => fallback(t)); }
  else fallback(t);
}
function fallback(t: string) {
  const ta = document.createElement('textarea');
  ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0';
  document.body.appendChild(ta); ta.focus(); ta.select();
  try { document.execCommand('copy'); } catch { /* ignore */ }
  ta.remove();
}

export function ControlPanel() {
  const rec = useRecording();
  const sim = getSim(rec.simId)!;
  const [copied, setCopied] = useState<string | null>(null);

  const exportUrl = (label: string, mode: 'present' | 'render' | 'audiocap') => {
    copyText(buildObsUrl({
      sim: rec.simId, script: rec.script, mode,
      bg: rec.bg.url, bgKind: rec.bg.kind, bgLoop: rec.bg.loop,
    }));
    setCopied(label);
    setTimeout(() => setCopied((c) => (c === label ? null : c)), 1600);
  };

  return (
    <aside className="panel">
      <div className="panel-head">
        <span className="panel-title">{sim.glyph} {sim.label}</span>
        <button className="linklike" onClick={() => rec.setScript(sim.defaultScript)}>reset script</button>
      </div>

      <section className="panel-sec">
        <label className="panel-label">Script</label>
        <textarea
          className="script"
          spellCheck={false}
          value={rec.script}
          onChange={(e) => rec.setScript(e.target.value)}
          placeholder="Type the script… use [[wrong|right]] for a typo-and-correct."
        />
      </section>

      <section className="panel-sec">
        <div className="btn-row">
          <button className="btn primary" disabled={rec.playing} onClick={() => rec.play()}>▶ Preview</button>
          <button className="btn" disabled={!rec.playing} onClick={() => rec.stop()}>■ Stop</button>
        </div>
      </section>

      <section className="panel-sec">
        <label className="panel-label">Background media (for OBS)</label>
        <input
          className="text-in"
          type="text"
          placeholder="Direct URL (mp4 / webm / mp3 / m4a…)"
          value={rec.bg.url || ''}
          onChange={(e) => {
            const url = e.target.value.trim();
            rec.setBg({ url: url || null, kind: url ? guessKind(url) : rec.bg.kind });
          }}
        />
        <div className="row">
          <span className="mini">Vol</span>
          <input type="range" min={0} max={1} step={0.01} value={rec.bg.volume}
            onChange={(e) => rec.setBg({ volume: parseFloat(e.target.value) })} />
          <label className="mini check">
            <input type="checkbox" checked={rec.bg.loop} onChange={(e) => rec.setBg({ loop: e.target.checked })} /> loop
          </label>
          {rec.bg.url && <button className="linklike" onClick={() => rec.setBg({ url: null })}>clear</button>}
        </div>
      </section>

      <section className="panel-sec">
        <label className="panel-label">Copy OBS Browser-Source URL</label>
        <div className="btn-row">
          <button className="btn" onClick={() => exportUrl('present', 'present')}>{copied === 'present' ? '✓ copied' : 'Present'}</button>
          <button className="btn" onClick={() => exportUrl('render', 'render')}>{copied === 'render' ? '✓ copied' : 'Video'}</button>
          <button className="btn" onClick={() => exportUrl('audiocap', 'audiocap')}>{copied === 'audiocap' ? '✓ copied' : 'Audio'}</button>
        </div>
        <p className="hint">
          <b>Present</b> = audible preview take · <b>Video</b> = OBS pass (muted keys + sync marker) ·
          <b>Audio</b> = clean keystroke .webm. Mux with <code>remux.js</code>. See <code>legacy/RENDER.md</code>.
        </p>
      </section>
    </aside>
  );
}
