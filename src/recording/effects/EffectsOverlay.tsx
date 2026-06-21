import type { CSSProperties } from 'react';
import { useEffectsState } from './EffectsProvider';
import type { Annotation, Toast } from './EffectsProvider';

/** The camera transform applied to the sim content (zoom that persists until
    changed). Scales around the focus point, so a punched-in field stays put and
    grows rather than drifting. */
export function cameraStyle(cam: { z: number; cx: number; cy: number; ms: number }): CSSProperties {
  return {
    transform: `scale(${cam.z})`,
    transformOrigin: `${cam.cx * 100}% ${cam.cy * 100}%`,
    transition: `transform ${cam.ms}ms cubic-bezier(.4,0,.2,1)`,
    width: '100%',
    height: '100%',
  };
}

/* Everything that floats above the sim, in normalized logical space. Notifs and
   cursor sit OUTSIDE the camera zoom (they're screen furniture); annotations and
   the spotlight point at on-screen positions. */
export function EffectsOverlay() {
  const s = useEffectsState();
  return (
    <div className="fx-overlay">
      {s.lens && (
        <div
          className="fx-spotlight"
          style={{
            ['--sx' as any]: s.lens.x * 100 + '%',
            ['--sy' as any]: s.lens.y * 100 + '%',
            ['--sr' as any]: s.lens.r * 100 + '%',
          }}
        />
      )}

      {s.annotations.length > 0 && <Annotations list={s.annotations} />}

      <div className="fx-toasts">
        {s.toasts.map((t, i) => <ToastCard key={t.id} t={t} i={i} />)}
      </div>

      <FakeCursor x={s.cursor.x} y={s.cursor.y} ms={s.cursor.ms} visible={s.cursor.visible} down={s.cursor.down} />

      {s.subtitle && (
        <div className="fx-subtitle"><span>{s.subtitle}</span></div>
      )}
    </div>
  );
}

function ToastCard({ t, i }: { t: Toast; i: number }) {
  return (
    <div className="fx-toast" style={{ ['--i' as any]: i }}>
      <span className="fx-toast-ic" style={t.accent ? { background: t.accent } : undefined}>
        {t.icon ?? t.from.slice(0, 1).toUpperCase()}
      </span>
      <span className="fx-toast-body">
        <span className="fx-toast-from">{t.from}</span>
        <span className="fx-toast-subject">{t.subject}</span>
        {t.body && <span className="fx-toast-text">{t.body}</span>}
      </span>
    </div>
  );
}

function FakeCursor({ x, y, ms, visible, down }: { x: number; y: number; ms: number; visible: boolean; down: boolean }) {
  if (!visible) return null;
  return (
    <div
      className={'fx-cursor' + (down ? ' down' : '')}
      style={{ left: x * 100 + '%', top: y * 100 + '%', transition: `left ${ms}ms cubic-bezier(.3,.7,.2,1), top ${ms}ms cubic-bezier(.3,.7,.2,1)` }}
    >
      <svg viewBox="0 0 24 24" width="64" height="64" aria-hidden>
        <path d="M5 3l14 8.5-6 1.2 3.4 6.6-2.7 1.4L12.4 14l-4.4 4.2z" fill="#fff" stroke="#1a1a1a" strokeWidth="1.2" strokeLinejoin="round" />
      </svg>
      {down && <span className="fx-cursor-ring" />}
    </div>
  );
}

function Annotations({ list }: { list: Annotation[] }) {
  return (
    <svg className="fx-annot" viewBox="0 0 100 100" preserveAspectRatio="none">
      {list.map((a) => <AnnotationShape key={a.id} a={a} />)}
    </svg>
  );
}

function AnnotationShape({ a }: { a: Annotation }) {
  const W = (a as any).width ?? 0.5;
  if (a.kind === 'box') {
    const [x, y, w, h] = a.rect;
    return (
      <g>
        <rect x={x * 100} y={y * 100} width={w * 100} height={h * 100} fill="none" stroke={a.color} strokeWidth={W} vectorEffect="non-scaling-stroke" rx={1.2} />
        {a.label && <AnnotLabel x={x * 100} y={y * 100 - 1.2} text={a.label} color={a.color} />}
      </g>
    );
  }
  const [fx, fy] = a.from;
  const [tx, ty] = a.to;
  const x1 = fx * 100, y1 = fy * 100, x2 = tx * 100, y2 = ty * 100;
  if (a.kind === 'string') {
    const mx = (x1 + x2) / 2;
    const my = (y1 + y2) / 2 + a.curve * 100;
    return (
      <g>
        <path d={`M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`} fill="none" stroke={a.color} strokeWidth={W} vectorEffect="non-scaling-stroke" strokeLinecap="round" />
        {a.label && <AnnotLabel x={mx} y={my} text={a.label} color={a.color} />}
      </g>
    );
  }
  // arrow
  const ang = Math.atan2(y2 - y1, x2 - x1);
  const ah = 2.4;
  const a1x = x2 - ah * Math.cos(ang - 0.4), a1y = y2 - ah * Math.sin(ang - 0.4);
  const a2x = x2 - ah * Math.cos(ang + 0.4), a2y = y2 - ah * Math.sin(ang + 0.4);
  return (
    <g>
      <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={a.color} strokeWidth={W} vectorEffect="non-scaling-stroke" strokeLinecap="round" />
      <polyline points={`${a1x},${a1y} ${x2},${y2} ${a2x},${a2y}`} fill="none" stroke={a.color} strokeWidth={W} vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round" />
      {a.label && <AnnotLabel x={x1} y={y1 - 1.2} text={a.label} color={a.color} />}
    </g>
  );
}

function AnnotLabel({ x, y, text, color }: { x: number; y: number; text: string; color: string }) {
  return (
    <text x={x} y={y} fill={color} fontSize={3} fontWeight={700} textAnchor="middle" style={{ paintOrder: 'stroke', stroke: 'rgba(0,0,0,.35)', strokeWidth: 0.6 }}>
      {text}
    </text>
  );
}
