import { useRecording } from '../recording/useRecording';
import { SIMS } from '../sims/registry';

export function Sidebar() {
  const rec = useRecording();
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-dot" />
        Sim&nbsp;Hub
      </div>
      <nav className="applist">
        {SIMS.map((s, i) => (
          <button
            key={s.id}
            className={'appitem' + (s.id === rec.simId ? ' active' : '') + (s.ready ? '' : ' soon')}
            disabled={!s.ready}
            onClick={() => s.ready && rec.setSimId(s.id)}
            style={{ ['--accent' as any]: s.accent }}
            title={s.ready ? s.label : s.label + ' — coming soon'}
          >
            <span className="appitem-glyph">{s.glyph}</span>
            <span className="appitem-label">{s.label}</span>
            <span className="appitem-key">{i + 1}</span>
            {!s.ready && <span className="appitem-soon">soon</span>}
          </button>
        ))}
      </nav>
      <div className="sidebar-foot">OBS-ready · {SIMS.filter((s) => s.ready).length}/{SIMS.length} built</div>
    </aside>
  );
}
