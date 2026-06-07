import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';

/* Renders children at a fixed logical size (e.g. 1080x1920) and scales them to
   fit the container, preserving aspect. So the sim lays out once and reads
   identically at any OBS source resolution (a 2160x3840 source just supersamples
   the same layout — crisp, not stretched). */
export function StageFrame({ w, h, children }: { w: number; h: number; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(0);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => {
      const r = el.getBoundingClientRect();
      if (r.width && r.height) setScale(Math.min(r.width / w, r.height / h));
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, [w, h]);

  return (
    <div className="stageframe" ref={ref}>
      <div
        className="stageframe-inner"
        style={{ width: w, height: h, transform: `translate(-50%, -50%) scale(${scale})`, visibility: scale ? 'visible' : 'hidden' }}
      >
        {children}
      </div>
    </div>
  );
}
