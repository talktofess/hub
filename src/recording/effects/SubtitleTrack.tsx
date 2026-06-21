import { useEffect, useMemo, useRef } from 'react';
import { useRecording } from '../useRecording';
import { useEffects } from './EffectsProvider';
import { parseSrt, activeCue } from './srt';

/* Drives subtitles from a pre-written SRT track on the take clock. Only active
   when an SRT track is present and subtitles are on — otherwise inline
   [[say:]] directives drive captions instead. */
export function SubtitleTrack() {
  const rec = useRecording();
  const fxc = useEffects();
  const cues = useMemo(() => (rec.settings.srt ? parseSrt(rec.settings.srt) : []), [rec.settings.srt]);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const last = useRef<string | null>(null);

  const enabled = rec.settings.subtitles && cues.length > 0;

  useEffect(() => {
    if (!enabled || rec.playSignal === 0) return;
    const t0 = performance.now();
    last.current = null;
    tickRef.current = setInterval(() => {
      const t = (performance.now() - t0) / 1000;
      const cue = activeCue(cues, t);
      if (cue !== last.current) { last.current = cue; fxc.setSubtitle(cue); }
    }, 100);
    return () => { if (tickRef.current) clearInterval(tickRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.playSignal, enabled]);

  useEffect(() => {
    if (rec.stopSignal === 0) return;
    if (tickRef.current) clearInterval(tickRef.current);
    fxc.setSubtitle(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.stopSignal]);

  return null;
}
