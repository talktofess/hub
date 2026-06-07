import { useCallback, useRef } from 'react';
import { useRecording } from './useRecording';
import { charDelay, fumbleFor, sleep, tokenize } from './typer';
import type { TypeStep } from './typer';
import type { Settings } from './settings';

/* Runs a sim's TypeStep plan with the universal realism settings: start delay,
   speed, jitter, hesitation, auto-typos, keystroke audio, the #render sync
   marker, SRT pacing, and optional looping. The shell decides WHEN to start
   (play/stop signals); sims pass a factory so each loop iteration gets a fresh
   plan (state reset). */
export function useTypewriter() {
  const rec = useRecording();
  const tokenRef = useRef(0);

  const stop = useCallback(() => {
    tokenRef.current++;
    rec.setPlaying(false);
  }, [rec]);

  const run = useCallback(async (planFactory: () => TypeStep[]) => {
    const myToken = ++tokenRef.current;
    const aborted = () => tokenRef.current !== myToken;
    const st = rec.settings;
    const speed = st.speed || 1;

    rec.setPlaying(true);
    await rec.audio.resume();

    do {
      const plan = planFactory();
      await sleep(st.startDelay / speed);
      if (aborted()) return;
      const finished = await runOnce(plan, st, speed, aborted);
      if (!finished) return;
      if (st.loop && !rec.oneShot) await sleep(st.holdEnd);
    } while (st.loop && !rec.oneShot && !aborted());

    if (aborted()) return;
    rec.setPlaying(false);
    if (rec.capturing) setTimeout(() => rec.audio.stopCapture(), 700);
  }, [rec]);

  const runOnce = useCallback(async (
    plan: TypeStep[], st: Settings, speed: number, aborted: () => boolean,
  ): Promise<boolean> => {
    const t0 = performance.now();
    let firstKey = false;
    const tap = () => {
      if (!firstKey) { firstKey = true; if (rec.mode === 'render') rec.flashMarker(); }
      rec.audio.key();
    };

    for (const step of plan) {
      if (aborted()) return false;
      if (step.kind === 'pause') { await sleep(step.ms); continue; }
      if (step.kind === 'reveal') { step.fn(); if (step.delay) await sleep(step.delay); continue; }

      if (step.cue != null) {
        while (!aborted() && rec.elapsed(t0) < step.cue * 1000) await sleep(30);
      }

      let cur = '';
      for (const a of tokenize(step.text)) {
        if (aborted()) return false;
        if (a.t === 'wait') { await sleep(a.ms / speed); continue; }
        if (a.t === 'back') {
          cur = cur.slice(0, -1); step.onUpdate(cur);
          await sleep((45 + Math.random() * 35) / speed); continue;
        }
        // auto-typo: occasionally fumble a letter, then self-correct
        if (st.autoTypo > 0 && Math.random() < st.autoTypo) {
          const wrong = fumbleFor(a.ch);
          if (wrong) {
            cur += wrong; step.onUpdate(cur); tap();
            await sleep(charDelay(a.ch, st) / speed);
            await sleep((180 + Math.random() * 160) / speed);
            cur = cur.slice(0, -1); step.onUpdate(cur);
            await sleep((60 + Math.random() * 40) / speed);
          }
        }
        cur += a.ch; step.onUpdate(cur); tap();
        await sleep(charDelay(a.ch, st) / speed);
      }
    }
    return true;
  }, [rec]);

  return { run, stop };
}
