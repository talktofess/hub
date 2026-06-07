import { useState } from 'react';
import { useRecording } from '../recording/useRecording';
import { getSim } from '../sims/registry';
import { buildObsUrl } from '../recording/url';

function copyText(t: string) {
  if (navigator.clipboard) navigator.clipboard.writeText(t).catch(() => fallback(t));
  else fallback(t);
}
function fallback(t: string) {
  const ta = document.createElement('textarea');
  ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0';
  document.body.appendChild(ta); ta.focus(); ta.select();
  try { document.execCommand('copy'); } catch { /* ignore */ }
  ta.remove();
}

export function ControlPanel({ onOpenSettings }: { onOpenSettings: () => void }) {
  const rec = useRecording();
  const sim = getSim(rec.simId)!;
  const [copied, setCopied] = useState(false);

  const copyUrl = () => {
    copyText(buildObsUrl(rec.simId, rec.script, rec.settings));
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <aside className="panel">
      <div className="panel-head">
        <span className="panel-title">{sim.glyph} {sim.label}</span>
        <button className="linklike" onClick={() => rec.setScript(sim.defaultScript)}>reset script</button>
      </div>

      <section className="panel-sec panel-grow">
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
          <button className="btn" disabled={rec.playing} onClick={() => rec.play()}>▶ Preview</button>
          <button className="btn" disabled={!rec.playing} onClick={() => rec.stop()}>■ Stop</button>
        </div>
      </section>

      <section className="panel-sec">
        <button className="btn primary big" onClick={copyUrl}>
          {copied ? '✓ Copied — paste into OBS' : '📋 Copy OBS URL'}
        </button>
        <p className="hint">
          One URL — copy it <b>once</b> into an OBS Browser Source. It carries this sim, its
          script, and all universal settings (typing sound, speed, background). OBS records
          the take directly.
        </p>
        <button className="linklike" onClick={onOpenSettings}>⚙ Universal settings (sound, speed, background)</button>
      </section>
    </aside>
  );
}
