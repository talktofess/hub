import { useEffect } from 'react';
import { useRecording } from './useRecording';

/* Full-screen background media behind the stage. A video fills the frame; audio
   plays with the gradient showing. It also drives the SRT clock (typing follows
   this element's playhead). Plays while recording/playing, pauses in edit. */
export function BackgroundMedia() {
  const rec = useRecording();
  const { bg, bgRef, playing, isRecording } = rec;

  useEffect(() => {
    const el = bgRef.current;
    if (!el || !bg.url) return;
    el.volume = bg.volume;
    el.loop = bg.loop;
    if (isRecording || playing) {
      const p = el.play();
      if (p && p.catch) p.catch(() => {});
    } else {
      try { el.pause(); el.currentTime = 0; } catch { /* ignore */ }
    }
  }, [bg.url, bg.volume, bg.loop, playing, isRecording, bgRef]);

  const showVideo = !!bg.url && bg.kind === 'video';
  return (
    <video
      ref={bgRef}
      src={bg.url || undefined}
      playsInline
      className="bg-media"
      style={{ opacity: showVideo ? 1 : 0 }}
      aria-hidden
    />
  );
}
