import { useCallback, useRef } from 'react';
import { useRecording } from './useRecording';
import { charDelay, sleep, tokenize } from './typer';
import type { TypeStep } from './typer';

/* Runs a sim's TypeStep plan: keystroke audio per char, the sync marker on the
   first keystroke (#render), SRT-paced cues (typing follows BG audio), and clean
   abort. The "when to start" policy lives in the shell — sims just call run() in
   response to playSignal and stop() on stopSignal. */
export function useTypewriter() {
  const rec = useRecording();
  const tokenRef = useRef(0);

  const stop = useCallback(() => {
    tokenRef.current++;
    rec.setPlaying(false);
  }, [rec]);

  const run = useCallback(async (plan: TypeStep[]) => {
    const myToken = ++tokenRef.current;
    const aborted = () => tokenRef.current !== myToken;

    rec.setPlaying(true);
    await rec.audio.resume();
    const t0 = performance.now();
    const speed = rec.settings.speed || 1; // universal typing-rate multiplier
    let firstKey = false;

    for (const step of plan) {
      if (aborted()) return;

      if (step.kind === 'pause') { await sleep(step.ms); continue; }
      if (step.kind === 'reveal') { step.fn(); if (step.delay) await sleep(step.delay); continue; }

      // SRT pacing: hold this line until the audio playhead reaches its cue.
      if (step.cue != null) {
        while (!aborted() && rec.elapsed(t0) < step.cue * 1000) await sleep(30);
      }

      let cur = '';
      for (const a of tokenize(step.text)) {
        if (aborted()) return;
        if (a.t === 'wait') { await sleep(a.ms / speed); continue; }
        if (a.t === 'back') {
          cur = cur.slice(0, -1);
          step.onUpdate(cur);
          await sleep((45 + Math.random() * 35) / speed);
          continue;
        }
        cur += a.ch;
        step.onUpdate(cur);
        if (!firstKey) {
          firstKey = true;
          if (rec.mode === 'render') rec.flashMarker();
        }
        rec.audio.key(); // muted via master gain in #render; keeps timing identical
        await sleep(charDelay(a.ch) / speed);
      }
    }

    if (aborted()) return;
    rec.setPlaying(false);
    if (rec.capturing) setTimeout(() => rec.audio.stopCapture(), 700); // finalize the take
  }, [rec]);

  return { run, stop };
}
