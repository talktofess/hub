import { useSimSettings } from '../../recording/useSimSettings';
import type { EmailSettings, InboxRow } from './settings';
import { DEFAULT_INBOX } from './settings';

function useEmail() {
  return useSimSettings<EmailSettings>();
}

export function LayoutTab() {
  const [s, set] = useEmail();
  return (
    <div className="tab-pane">
      <label className="panel-label">Layout</label>
      <div className="seg">
        <button className={s.layout === 'desktop' ? 'on' : ''} onClick={() => set({ layout: 'desktop' })}>🖥 Desktop</button>
        <button className={s.layout === 'reel' ? 'on' : ''} onClick={() => set({ layout: 'reel' })}>📱 Reel</button>
      </div>
      <p className="hint">
        Desktop renders the full Gmail web app (1920×1080). Reel is the vertical
        mobile compose (1080×1920). Same script drives both.
      </p>
      {s.layout === 'desktop' && (
        <>
          <label className="panel-label">Chrome density — {Math.round(s.density * 100)}%</label>
          <input type="range" min={0.8} max={1.2} step={0.01} value={s.density} onChange={(e) => set({ density: +e.target.value })} />
        </>
      )}
    </div>
  );
}

export function GmailTab() {
  const [s, set] = useEmail();
  return (
    <div className="tab-pane">
      <label className="panel-label">Gmail theme</label>
      <div className="seg">
        <button className={s.theme === 'light' ? 'on' : ''} onClick={() => set({ theme: 'light' })}>Light</button>
        <button className={s.theme === 'dark' ? 'on' : ''} onClick={() => set({ theme: 'dark' })}>Dark</button>
      </div>
      <div className="field-row" style={{ marginTop: 8 }}>
        <label>Accent</label>
        <input type="color" value={s.accent} onChange={(e) => set({ accent: e.target.value })} />
        <button className="linklike" onClick={() => set({ accent: '#0b57d0' })}>reset</button>
      </div>
      {s.layout === 'desktop' && (
        <label className="mini check" style={{ marginTop: 8 }}>
          <input type="checkbox" checked={s.sidebar} onChange={(e) => set({ sidebar: e.target.checked })} /> show sidebar
        </label>
      )}
    </div>
  );
}

export function ComposeTab() {
  const [s, set] = useEmail();
  return (
    <div className="tab-pane">
      <label className="mini check">
        <input type="checkbox" checked={s.showCc} onChange={(e) => set({ showCc: e.target.checked })} /> show Cc row
      </label>
      <p className="hint">From / To / Subject / Body come from the script. Use <code>[[cc]]</code> in the script to type into Cc.</p>
      {s.layout === 'desktop' && (
        <>
          <label className="panel-label">Compose window</label>
          <div className="seg">
            <button className={s.composePos === 'br' ? 'on' : ''} onClick={() => set({ composePos: 'br' })}>Docked ↘</button>
            <button className={s.composePos === 'center' ? 'on' : ''} onClick={() => set({ composePos: 'center' })}>Centered</button>
          </div>
        </>
      )}
    </div>
  );
}

export function CameraTab() {
  const [s, set] = useEmail();
  return (
    <div className="tab-pane">
      <label className="mini check">
        <input type="checkbox" checked={s.camFollow} onChange={(e) => set({ camFollow: e.target.checked })} /> auto-zoom to the active field
      </label>
      <label className="panel-label">Zoom level — {s.camZoom.toFixed(2)}×</label>
      <input type="range" min={1} max={2.4} step={0.05} value={s.camZoom} onChange={(e) => set({ camZoom: +e.target.value })} />
      <label className="mini check">
        <input type="checkbox" checked={s.camPersist} onChange={(e) => set({ camPersist: e.target.checked })} /> persist the zoomed-in look (hold, don't snap back)
      </label>
      <label className="mini check">
        <input type="checkbox" checked={s.cursor} onChange={(e) => set({ cursor: e.target.checked })} /> fake cursor moves &amp; clicks each field
      </label>
      <p className="hint">
        Manual control from the script too: <code>[[zoom:body]]</code>, <code>[[zoom:0.5,0.3,1.8]]</code>,
        <code>[[zoomout]]</code>, <code>[[lens:0.5,0.3]]</code>, <code>[[cursor:send]]</code>, <code>[[click]]</code>.
      </p>
    </div>
  );
}

export function NotifTab() {
  const [s, set] = useEmail();
  return (
    <div className="tab-pane">
      <p className="hint">Fire a slide-in notification from the script: <code>[[notif:GitHub|CI passed|all checks green]]</code>. They stack and auto-dismiss.</p>
      <label className="mini check">
        <input type="checkbox" checked={s.notifSound} onChange={(e) => set({ notifSound: e.target.checked })} /> play a chime
      </label>
      <label className="panel-label">On-screen time — {(s.notifMs / 1000).toFixed(1)}s</label>
      <input type="range" min={1500} max={8000} step={100} value={s.notifMs} onChange={(e) => set({ notifMs: +e.target.value })} />
    </div>
  );
}

export function InboxTab() {
  const [s, set] = useEmail();
  const rows = s.inbox;

  const update = (i: number, patch: Partial<InboxRow>) => {
    const next = rows.map((r, j) => (j === i ? { ...r, ...patch } : r));
    set({ inbox: next });
  };
  const remove = (i: number) => set({ inbox: rows.filter((_, j) => j !== i) });
  const add = () => set({ inbox: [...rows, { from: 'Sender', subject: 'Subject', snippet: 'A short preview…', time: 'now', unread: true }] });

  return (
    <div className="tab-pane">
      <p className="hint">The inbox list shown behind the compose window (desktop). Edit, reorder by adding, toggle unread (bold).</p>
      {rows.map((r, i) => (
        <div className="list-card" key={i}>
          <div className="list-row">
            <input className="text-in" value={r.from} placeholder="From" onChange={(e) => update(i, { from: e.target.value })} />
            <input className="text-in" style={{ flex: '0 0 90px' }} value={r.time} placeholder="time" onChange={(e) => update(i, { time: e.target.value })} />
            <button className="icon-btn danger" title="remove" onClick={() => remove(i)}>×</button>
          </div>
          <input className="text-in" value={r.subject} placeholder="Subject" onChange={(e) => update(i, { subject: e.target.value })} />
          <input className="text-in" value={r.snippet} placeholder="Preview snippet" onChange={(e) => update(i, { snippet: e.target.value })} />
          <label className="mini check">
            <input type="checkbox" checked={r.unread} onChange={(e) => update(i, { unread: e.target.checked })} /> unread (bold)
          </label>
        </div>
      ))}
      <div className="btn-row">
        <button className="btn" onClick={add}>+ Add email</button>
        <button className="btn" onClick={() => set({ inbox: DEFAULT_INBOX })}>Reset</button>
      </div>
    </div>
  );
}
