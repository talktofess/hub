import { useRecording } from '../recording/useRecording';
import { DISPLAY_MODES, SOUND_PROFILES, SPEED_PRESETS } from '../recording/settings';
import { MediaLibrary } from './MediaLibrary';

function guessKind(url: string): 'image' | 'video' | 'audio' {
  if (/\.(png|jpe?g|gif|webp|avif|svg)(\?|#|$)/i.test(url)) return 'image';
  if (/\.(mp3|wav|m4a|aac|flac|oga|ogg)(\?|#|$)/i.test(url)) return 'audio';
  return 'video';
}

export function SettingsPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  const testSound = async () => { await rec.audio.resume(); rec.audio.key(); };

  return (
    <>
      <div className={'drawer-scrim' + (open ? ' show' : '')} onClick={onClose} />
      <aside className={'drawer' + (open ? ' open' : '')} aria-hidden={!open}>
        <div className="drawer-head">
          <span className="panel-title">⚙ Universal settings</span>
          <button className="linklike" onClick={onClose}>close</button>
        </div>
        <p className="hint">Universal — these apply to <b>every</b> sim and travel to OBS in the one URL.</p>

        <details className="grp" open>
          <summary>Typing sound</summary>
          <div className="row">
            <select className="select" value={s.sound} onChange={(e) => set({ sound: e.target.value as any })}>
              {SOUND_PROFILES.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
            </select>
            <button className="btn" style={{ flex: '0 0 auto' }} onClick={testSound}>Test</button>
          </div>
          <label className="panel-label">Keystroke volume — {Math.round(s.volume * 100)}%</label>
          <input type="range" min={0} max={1} step={0.01} value={s.volume} onChange={(e) => set({ volume: +e.target.value })} />
        </details>

        <details className="grp" open>
          <summary>Typing speed & realism</summary>
          <label className="panel-label">Speed — {s.speed.toFixed(2)}×</label>
          <div className="btn-row">
            {SPEED_PRESETS.map((p) => (
              <button key={p.label} className={'btn' + (Math.abs(p.value - s.speed) < 0.001 ? ' primary' : '')}
                onClick={() => set({ speed: p.value })}>{p.label}</button>
            ))}
          </div>
          <input type="range" min={0.4} max={2.6} step={0.05} value={s.speed} onChange={(e) => set({ speed: +e.target.value })} />
          <label className="panel-label">Hesitation — {Math.round(s.thinkPauses * 100)}%</label>
          <input type="range" min={0} max={1} step={0.01} value={s.thinkPauses} onChange={(e) => set({ thinkPauses: +e.target.value })} />
          <label className="panel-label">Timing jitter — {Math.round(s.jitter * 100)}%</label>
          <input type="range" min={0} max={1} step={0.01} value={s.jitter} onChange={(e) => set({ jitter: +e.target.value })} />
          <label className="panel-label">Auto mistakes — {Math.round(s.autoTypo * 100)}%</label>
          <input type="range" min={0} max={0.15} step={0.005} value={s.autoTypo} onChange={(e) => set({ autoTypo: +e.target.value })} />
          <label className="panel-label">Start delay — {s.startDelay} ms</label>
          <input type="range" min={0} max={3000} step={50} value={s.startDelay} onChange={(e) => set({ startDelay: +e.target.value })} />
          <div className="row">
            <label className="mini check"><input type="checkbox" checked={s.loop} onChange={(e) => set({ loop: e.target.checked })} /> loop take</label>
            <span className="mini">hold {s.holdEnd}ms</span>
            <input type="range" min={0} max={4000} step={100} value={s.holdEnd} onChange={(e) => set({ holdEnd: +e.target.value })} />
          </div>
        </details>

        <details className="grp">
          <summary>Caret</summary>
          <div className="row">
            <label className="mini check"><input type="checkbox" checked={s.showCaret} onChange={(e) => set({ showCaret: e.target.checked })} /> show</label>
            <label className="mini check"><input type="checkbox" checked={s.caretBlink} onChange={(e) => set({ caretBlink: e.target.checked })} /> blink</label>
            <input type="color" value={s.caretColor} onChange={(e) => set({ caretColor: e.target.value })} />
          </div>
          <div className="btn-row">
            {(['bar', 'block', 'underline'] as const).map((c) => (
              <button key={c} className={'btn' + (s.caretStyle === c ? ' primary' : '')} onClick={() => set({ caretStyle: c })}>{c}</button>
            ))}
          </div>
        </details>

        <details className="grp">
          <summary>Look & feel</summary>
          <div className="btn-row">
            {(['auto', 'light', 'dark'] as const).map((t) => (
              <button key={t} className={'btn' + (s.theme === t ? ' primary' : '')} onClick={() => set({ theme: t })}>{t}</button>
            ))}
          </div>
          <div className="row">
            <span className="mini">Accent</span>
            <input type="color" value={s.accent} onChange={(e) => set({ accent: e.target.value })} />
            <label className="mini check"><input type="checkbox" checked={s.grain} onChange={(e) => set({ grain: e.target.checked })} /> grain</label>
            <label className="mini check"><input type="checkbox" checked={s.vignette} onChange={(e) => set({ vignette: e.target.checked })} /> vignette</label>
          </div>
          <label className="panel-label">Text scale — {Math.round(s.fontScale * 100)}%</label>
          <input type="range" min={0.8} max={1.3} step={0.01} value={s.fontScale} onChange={(e) => set({ fontScale: +e.target.value })} />
        </details>

        <details className="grp" open>
          <summary>Background media</summary>
          <MediaLibrary />
          <input className="text-in" type="text" placeholder="…or a direct URL (image / video / audio)"
            value={!rec.bg.mediaId && rec.bg.url ? rec.bg.url : ''}
            onChange={(e) => {
              const url = e.target.value.trim();
              rec.setBg({ url: url || null, mediaId: null, kind: url ? guessKind(url) : rec.bg.kind });
            }} />
          {rec.bg.url && (
            <>
              <label className="panel-label">Display mode</label>
              <select className="select" value={s.bg.mode} onChange={(e) => rec.setBg({ mode: e.target.value as any })}>
                {DISPLAY_MODES.map((m) => <option key={m.id} value={m.id}>{m.label}</option>)}
              </select>
              <div className="row">
                <label className="mini check"><input type="checkbox" checked={s.bg.kenBurns} onChange={(e) => rec.setBg({ kenBurns: e.target.checked })} /> Ken Burns</label>
                {s.bg.kind === 'video' && <label className="mini check"><input type="checkbox" checked={s.bg.audioOnly} onChange={(e) => rec.setBg({ audioOnly: e.target.checked })} /> audio only</label>}
                <label className="mini check"><input type="checkbox" checked={s.bg.loop} onChange={(e) => rec.setBg({ loop: e.target.checked })} /> loop</label>
              </div>
              <label className="panel-label">Media volume — {Math.round(s.bg.volume * 100)}%</label>
              <input type="range" min={0} max={1} step={0.01} value={s.bg.volume} onChange={(e) => rec.setBg({ volume: +e.target.value })} />
              <button className="linklike" onClick={() => rec.setBg({ url: null, mediaId: null })}>clear background</button>
            </>
          )}
        </details>
      </aside>
    </>
  );
}
