import { useEffect, useMemo, useState } from 'react';
import { useRecording } from '../recording/useRecording';
import { DISPLAY_MODES, SOUND_PROFILES, SPEED_PRESETS } from '../recording/settings';
import { getSim } from '../sims/registry';
import type { SimTab } from '../sims/types';
import { listVoices, onVoices, speak } from '../recording/effects/speech';
import { MediaLibrary } from './MediaLibrary';

function guessKind(url: string): 'image' | 'video' | 'audio' {
  if (/\.(png|jpe?g|gif|webp|avif|svg)(\?|#|$)/i.test(url)) return 'image';
  if (/\.(mp3|wav|m4a|aac|flac|oga|ogg)(\?|#|$)/i.test(url)) return 'audio';
  return 'video';
}

interface Tab { id: string; label: string; group: 'universal' | 'sim'; Panel: () => JSX.Element }

const UNIVERSAL: Tab[] = [
  { id: 'u-sound', label: 'Sound', group: 'universal', Panel: SoundPanel },
  { id: 'u-timing', label: 'Timing', group: 'universal', Panel: TimingPanel },
  { id: 'u-caret', label: 'Caret', group: 'universal', Panel: CaretPanel },
  { id: 'u-look', label: 'Look', group: 'universal', Panel: LookPanel },
  { id: 'u-media', label: 'Media', group: 'universal', Panel: MediaPanel },
  { id: 'u-narrate', label: 'Narrate', group: 'universal', Panel: NarratePanel },
];

export function SettingsPanel({ open, onClose }: { open: boolean; onClose: () => void }) {
  const rec = useRecording();
  const sim = getSim(rec.simId)!;

  const simTabs: SimTab[] = sim.settingsTabs ?? [];
  const tabs: Tab[] = useMemo(
    () => [
      ...UNIVERSAL,
      ...simTabs.map((t) => ({ id: 'sim-' + t.id, label: t.label, group: 'sim' as const, Panel: () => <t.Panel /> })),
    ],
    [simTabs],
  );

  const [active, setActive] = useState('u-sound');
  // if the active sim changed and its tab vanished, fall back to a universal tab
  useEffect(() => {
    if (!tabs.some((t) => t.id === active)) setActive('u-sound');
  }, [tabs, active]);

  const Current = tabs.find((t) => t.id === active)?.Panel ?? SoundPanel;

  return (
    <>
      <div className={'drawer-scrim' + (open ? ' show' : '')} onClick={onClose} />
      <aside className={'drawer drawer-tabbed' + (open ? ' open' : '')} aria-hidden={!open}>
        <div className="drawer-head">
          <span className="panel-title">⚙ Settings — {sim.glyph} {sim.label}</span>
          <button className="linklike" onClick={onClose}>close</button>
        </div>

        <div className="tab-strip">
          {tabs.map((t) => (
            <button
              key={t.id}
              className={'tab-btn' + (t.id === active ? ' active' : '') + (t.group === 'sim' ? ' sim' : '')}
              onClick={() => setActive(t.id)}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div className="tab-body">
          <Current />
        </div>
      </aside>
    </>
  );
}

/* ---------------- universal tab panels ---------------- */

function SoundPanel() {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  const testSound = async () => { await rec.audio.resume(); rec.audio.key(); };
  return (
    <div className="tab-pane">
      <p className="hint">Universal keystroke sound — applies to every sim (a sim may override it in its own tab).</p>
      <div className="row">
        <select className="select" value={s.sound} onChange={(e) => set({ sound: e.target.value as any })}>
          {SOUND_PROFILES.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
        </select>
        <button className="btn" style={{ flex: '0 0 auto' }} onClick={testSound}>Test</button>
      </div>
      <label className="panel-label">Keystroke volume — {Math.round(s.volume * 100)}%</label>
      <input type="range" min={0} max={1} step={0.01} value={s.volume} onChange={(e) => set({ volume: +e.target.value })} />
    </div>
  );
}

function TimingPanel() {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  return (
    <div className="tab-pane">
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
    </div>
  );
}

function CaretPanel() {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  return (
    <div className="tab-pane">
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
    </div>
  );
}

function LookPanel() {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  return (
    <div className="tab-pane">
      <label className="panel-label">Theme</label>
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
    </div>
  );
}

function NarratePanel() {
  const rec = useRecording();
  const s = rec.settings;
  const set = rec.setSettings;
  const [voices, setVoices] = useState<string[]>([]);
  useEffect(() => onVoices(() => setVoices(listVoices().map((v) => `${v.name}`))), []);
  const test = () => speak('This is the narration voice for your recording.', { voice: s.ttsVoice, rate: s.ttsRate, pitch: s.ttsPitch });

  return (
    <div className="tab-pane">
      <p className="hint">Captions + optional spoken voiceover for <code>[[say:…]]</code> lines in any sim's script. For OBS to record the voice, capture the browser-source (or desktop) audio.</p>
      <label className="mini check"><input type="checkbox" checked={s.subtitles} onChange={(e) => set({ subtitles: e.target.checked })} /> show subtitles on screen</label>
      <label className="mini check"><input type="checkbox" checked={s.narrate} onChange={(e) => set({ narrate: e.target.checked })} /> speak narration (TTS)</label>
      {s.narrate && (
        <>
          <div className="row">
            <select className="select" value={s.ttsVoice} onChange={(e) => set({ ttsVoice: e.target.value })}>
              <option value="">Default voice</option>
              {voices.map((v) => <option key={v} value={v}>{v}</option>)}
            </select>
            <button className="btn" style={{ flex: '0 0 auto' }} onClick={test}>Test</button>
          </div>
          <label className="panel-label">Rate — {s.ttsRate.toFixed(2)}×</label>
          <input type="range" min={0.6} max={1.6} step={0.05} value={s.ttsRate} onChange={(e) => set({ ttsRate: +e.target.value })} />
          <label className="panel-label">Pitch — {s.ttsPitch.toFixed(2)}</label>
          <input type="range" min={0.5} max={1.5} step={0.05} value={s.ttsPitch} onChange={(e) => set({ ttsPitch: +e.target.value })} />
        </>
      )}
      <label className="panel-label">SRT caption track (optional)</label>
      <textarea
        className="script" style={{ minHeight: 120 }} spellCheck={false}
        placeholder={'1\\n00:00:01,000 --> 00:00:04,000\\nPre-written captions, synced to the take clock.'}
        value={s.srt}
        onChange={(e) => set({ srt: e.target.value })}
      />
      <p className="hint">An SRT track here drives captions on the take clock. Leave empty to let inline <code>[[say:…]]</code> lines drive them instead.</p>
    </div>
  );
}

function MediaPanel() {
  const rec = useRecording();
  const s = rec.settings;
  return (
    <div className="tab-pane">
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
    </div>
  );
}
