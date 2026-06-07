import { useEffect, useState } from 'react';
import { useRecording } from './useRecording';

/* The deterministic-render sync marker: a white square flashed in the top-left
   of the frame on the first keystroke (#render only). remux.js locks the clean
   audio to this frame, then masks the square out of the final video. */
export function SyncMarker() {
  const rec = useRecording();
  const [on, setOn] = useState(false);

  useEffect(() => {
    if (rec.markerSignal === 0 || rec.mode !== 'render') return;
    setOn(true);
    const id = setTimeout(() => setOn(false), 130);
    return () => clearTimeout(id);
  }, [rec.markerSignal, rec.mode]);

  if (rec.mode !== 'render') return null;
  return (
    <div
      aria-hidden
      style={{
        position: 'fixed', left: 0, top: 0, width: 64, height: 64,
        background: '#fff', zIndex: 2147483647, opacity: on ? 1 : 0,
        pointerEvents: 'none',
      }}
    />
  );
}
