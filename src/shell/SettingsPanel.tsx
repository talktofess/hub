import { useRecording } from '../recording/useRecording';
import { SOUND_PROFILES, SPEED_PRESETS } from '../recording/settings';

function guessKind(url: string): 'video' | 'audio' {
  return /\.(mp3|wav|m4a|aac|flac|oga|ogg)(\?|#|$)/i.test(url) ? 'audio' : 'video';
}

export function SettingsPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  const rec = useRecording();
  const s = rec.settings;

  const testSound = async () => { await rec.audio.resume(); rec.audio.key(); };

  return (
    <>
      <div className={'drawer-scrim' + (open ? ' show' : '')} onClick={onClose} />
      <aside className={'drawer' + (open ? ' open' : '')} aria-hidden={!open}>
        <div className="drawer-head">
          <span className="panel-title">⚙ Universal settings</span>
          <button className="linklike" onClick={onClose}>close</button>
        </div>
        <p className="hint">Universal — these apply to <b>every</b> sim and are baked into the one OBS URL.</p>

        <section className="panel-sec">
          <label className="panel-label">Typing sound</label>
          <div className="row">
            <select className="select" value={s.sound} onChange={(e) => rec.setSettings({ sound: e.target.value as any })}>
              {SOUND_PROFILES.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
            </select>
            <button className="btn" style={{ flex: '0 0 auto' }} onClick={testSound}>Test</button>
          </div>
        </section>

        <section className="panel-sec">
          <label className="panel-label">Keystroke volume — {Math.round(s.volume * 100)}%</label>
          <input type="range" min={0} max={1} step={0.01} value={s.volume}
            onChange={(e) => rec.setSettings({ volume: parseFloat(e.target.value) })} />
        </section>

        <section className="panel-sec">
          <label className="panel-label">Typing speed — {s.speed.toFixed(2)}×</label>
          <div className="btn-row">
            {SPEED_PRESETS.map((p) => (
              <button key={p.label}
                className={'btn' + (Math.abs(p.value - s.speed) < 0.001 ? ' primary' : '')}
                onClick={() => rec.setSettings({ speed: p.value })}>{p.label}</button>
            ))}
          </div>
          <input type="range" min={0.4} max={2.6} step={0.05} value={s.speed}
            onChange={(e) => rec.setSettings({ speed: parseFloat(e.target.value) })} />
        </section>

        <section className="panel-sec">
          <label className="panel-label">Background media (plays under the take in OBS)</label>
          <input className="text-in" type="text" placeholder="Direct URL (mp4 / webm / mp3 / m4a…)"
            value={s.bg.url || ''}
            onChange={(e) => {
              const url = e.target.value.trim();
              rec.setBg({ url: url || null, kind: url ? guessKind(url) : s.bg.kind });
            }} />
          <div className="row">
            <span className="mini">Vol</span>
            <input type="range" min={0} max={1} step={0.01} value={s.bg.volume}
              onChange={(e) => rec.setBg({ volume: parseFloat(e.target.value) })} />
            <label className="mini check">
              <input type="checkbox" checked={s.bg.loop} onChange={(e) => rec.setBg({ loop: e.target.checked })} /> loop
            </label>
            {s.bg.url && <button className="linklike" onClick={() => rec.setBg({ url: null })}>clear</button>}
          </div>
          <p className="hint">Use a direct <code>https://</code> URL so OBS keeps it on reload. A video fills the
            frame behind the sim; audio just plays (and paces SRT typing).</p>
        </section>
      </aside>
    </>
  );
}
