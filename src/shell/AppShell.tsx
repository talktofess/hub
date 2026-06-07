import { useEffect, useState } from 'react';
import { useRecording } from '../recording/useRecording';
import { SIMS } from '../sims/registry';
import { Sidebar } from './Sidebar';
import { Stage } from './Stage';
import { ControlPanel } from './ControlPanel';
import { SettingsPanel } from './SettingsPanel';

export function AppShell() {
  const rec = useRecording();
  const [started, setStarted] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);

  // keyboard: digits switch ready sims, P preview, Esc stop (ignored in fields)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key >= '1' && e.key <= '9') {
        const s = SIMS[+e.key - 1];
        if (s && s.ready) { rec.setSimId(s.id); e.preventDefault(); }
      } else if (e.key === 'Escape') { rec.stop(); }
      else if (e.key.toLowerCase() === 'p') { rec.play(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [rec]);

  // present/render autostart one take; audiocap waits for a click (autoplay).
  useEffect(() => {
    if (rec.mode === 'present' || rec.mode === 'render') {
      const id = setTimeout(() => { setStarted(true); rec.play(); }, 900);
      return () => clearTimeout(id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (rec.isRecording) {
    return (
      <div className="rec-root">
        <Stage />
        {rec.capturing && !started && (
          <button className="cap-overlay" onClick={async () => { await rec.audio.resume(); setStarted(true); rec.play(); }}>
            <span className="cap-inner">
              <span className="cap-play">▶</span>
              <span>Click to render the audio track</span>
              <span className="cap-sub">records the clean keystrokes and downloads when typing ends</span>
            </span>
          </button>
        )}
        {rec.capturing && started && (
          <button className="cap-stop" onClick={() => rec.audio.stopCapture()}>■ Stop &amp; save</button>
        )}
      </div>
    );
  }

  return (
    <div className="edit-root">
      <Sidebar onOpenSettings={() => setSettingsOpen(true)} />
      <main className="stage-wrap"><Stage /></main>
      <ControlPanel onOpenSettings={() => setSettingsOpen(true)} />
      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  );
}
