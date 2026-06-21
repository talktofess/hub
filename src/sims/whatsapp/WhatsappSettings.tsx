import { useSimSettings } from '../../recording/useSimSettings';
import type { WaSettings } from './Whatsapp';

export function ChatTab() {
  const [s, set] = useSimSettings<WaSettings>();
  return (
    <div className="tab-pane">
      <label className="panel-label">Theme</label>
      <div className="seg">
        <button className={s.theme === 'dark' ? 'on' : ''} onClick={() => set({ theme: 'dark' })}>Dark</button>
        <button className={s.theme === 'light' ? 'on' : ''} onClick={() => set({ theme: 'light' })}>Light</button>
      </div>
      <label className="mini check" style={{ marginTop: 8 }}>
        <input type="checkbox" checked={s.wallpaper} onChange={(e) => set({ wallpaper: e.target.checked })} /> doodle wallpaper
      </label>
      <p className="hint">
        Contact via <code>[[name:Maya]]</code>. Story replies:
        <code>[[story:Maya|caption]]</code> (you reply to their status) or
        <code>[[recvstory:You|caption]]</code> (they reply to yours), then the next bubble.
      </p>
    </div>
  );
}
