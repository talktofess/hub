import { useRecording } from '../recording/useRecording';
import { useSimSettings } from '../recording/useSimSettings';
import { SOUND_PROFILES } from '../recording/settings';
import type { SoundProfile } from '../recording/settings';

/* A drop-in per-sim Sound tab. A sim whose feel depends on a particular sound
   (journal → pencil, typewriter → typewriter) puts `sound` / `soundVolume` in
   its defaultSettings and lists this as a tab. Setting it to 'inherit' clears
   the override so the universal sound applies again. */
interface SimSound { sound?: SoundProfile; soundVolume?: number }

export function SimSoundTab() {
  const rec = useRecording();
  const [s, set] = useSimSettings<SimSound>();
  const cur = s.sound ?? 'inherit';
  const vol = s.soundVolume ?? rec.settings.volume;
  const test = async () => { await rec.audio.resume(); rec.audio.key(); };

  return (
    <div className="tab-pane">
      <p className="hint">This sim's keystroke sound. <b>Inherit</b> uses the universal Sound tab.</p>
      <div className="row">
        <select
          className="select"
          value={cur}
          onChange={(e) => set({ sound: e.target.value === 'inherit' ? undefined : (e.target.value as SoundProfile) })}
        >
          <option value="inherit">Inherit (universal)</option>
          {SOUND_PROFILES.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
        </select>
        <button className="btn" style={{ flex: '0 0 auto' }} onClick={test}>Test</button>
      </div>
      {s.sound != null && (
        <>
          <label className="panel-label">Volume — {Math.round(vol * 100)}%</label>
          <input type="range" min={0} max={1} step={0.01} value={vol} onChange={(e) => set({ soundVolume: +e.target.value })} />
        </>
      )}
    </div>
  );
}
