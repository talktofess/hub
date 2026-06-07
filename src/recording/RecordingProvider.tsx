import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode, RefObject } from 'react';
import { AudioEngine } from './audio';
import type { CaptureResult } from './audio';
import { installSeededRandom } from './rng';
import { readBootConfig } from './url';
import type { BootConfig, Mode } from './url';
import { SIMS, defaultSimId, getSim } from '../sims/registry';

export interface BgState {
  url: string | null;
  kind: 'video' | 'audio';
  loop: boolean;
  volume: number;
}

export interface RecordingContextValue {
  mode: Mode;
  isRecording: boolean;
  oneShot: boolean;
  capturing: boolean;
  simId: string;
  setSimId: (id: string) => void;
  script: string;
  setScript: (s: string) => void;
  audio: AudioEngine;
  elapsed: (t0: number) => number;
  flashMarker: () => void;
  markerSignal: number;
  playSignal: number;
  stopSignal: number;
  play: () => void;
  stop: () => void;
  playing: boolean;
  setPlaying: (p: boolean) => void;
  bg: BgState;
  setBg: (next: Partial<BgState>) => void;
  bgRef: RefObject<HTMLVideoElement>;
  boot: BootConfig;
}

export const RecordingContext = createContext<RecordingContextValue | null>(null);

const scriptKey = (id: string) => 'sim:script:' + id;
const BG_KEY = 'hub:bgmedia';

function loadScript(id: string, fallback: string): string {
  try {
    const s = localStorage.getItem(scriptKey(id));
    if (s != null) return s;
  } catch { /* ignore */ }
  return fallback;
}

function loadBg(boot: BootConfig): BgState {
  if (boot.bg) return { url: boot.bg, kind: boot.bgKind || 'video', loop: boot.bgLoop, volume: 0.5 };
  try {
    const raw = localStorage.getItem(BG_KEY);
    if (raw) {
      const o = JSON.parse(raw);
      return { url: o.url || null, kind: o.kind || 'video', loop: o.loop !== false, volume: typeof o.volume === 'number' ? o.volume : 0.5 };
    }
  } catch { /* ignore */ }
  return { url: null, kind: 'video', loop: true, volume: 0.5 };
}

export function RecordingProvider({ children }: { children: ReactNode }) {
  const boot = useMemo(readBootConfig, []);
  const audio = useMemo(() => new AudioEngine(), []);
  const bgRef = useRef<HTMLVideoElement>(null);

  const mode = boot.mode;
  const isRecording = mode !== 'edit';
  const oneShot = isRecording;
  const capturing = mode === 'audiocap';

  const initialSim = boot.sim && getSim(boot.sim)?.ready ? boot.sim : defaultSimId();
  const [simId, setSimIdState] = useState(initialSim);

  const initialScript = boot.script != null ? boot.script : loadScript(initialSim, getSim(initialSim)?.defaultScript ?? '');
  const [script, setScriptState] = useState(initialScript);

  const [playing, setPlaying] = useState(false);
  const [playSignal, setPlaySignal] = useState(0);
  const [stopSignal, setStopSignal] = useState(0);
  const [markerSignal, setMarkerSignal] = useState(0);
  const [bg, setBgState] = useState<BgState>(() => loadBg(boot));

  // ---- seeded RNG for the reproducible render/audiocap passes ----
  useEffect(() => {
    if (mode === 'render' || mode === 'audiocap') {
      const restore = installSeededRandom(simId, script);
      return restore;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]); // script/sim are fixed for the lifetime of a record pass

  // ---- mute keystrokes in the video pass ----
  useEffect(() => { audio.setMuted(mode === 'render'); }, [audio, mode]);

  const setSimId = useCallback((id: string) => {
    const def = getSim(id);
    if (!def) return;
    setSimIdState(id);
    try { localStorage.setItem('hub:current', id); } catch { /* ignore */ }
    setScriptState(loadScript(id, def.defaultScript));
  }, []);

  const setScript = useCallback((s: string) => {
    setScriptState(s);
    if (!isRecording) { try { localStorage.setItem(scriptKey(simId), s); } catch { /* ignore */ } }
  }, [isRecording, simId]);

  const setBg = useCallback((next: Partial<BgState>) => {
    setBgState((prev) => {
      const merged = { ...prev, ...next };
      try { localStorage.setItem(BG_KEY, JSON.stringify(merged)); } catch { /* ignore */ }
      return merged;
    });
  }, []);

  const play = useCallback(() => setPlaySignal((n) => n + 1), []);
  const stop = useCallback(() => setStopSignal((n) => n + 1), []);
  const flashMarker = useCallback(() => setMarkerSignal((n) => n + 1), []);

  // SRT clock: typing follows the BG audio playhead when one is playing.
  const elapsed = useCallback((t0: number) => {
    const a = bgRef.current;
    if (a && a.currentSrc && !a.paused && isFinite(a.currentTime)) return a.currentTime * 1000;
    return performance.now() - t0;
  }, []);

  // download the clean track when an audiocap take finishes
  useEffect(() => {
    if (mode !== 'audiocap') return;
    audio.armCapture(simId, (r: CaptureResult) => {
      try {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(r.blob);
        a.download = r.name;
        document.body.appendChild(a);
        a.click();
        setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 2000);
      } catch { /* ignore */ }
    });
  }, [audio, mode, simId]);

  const value: RecordingContextValue = {
    mode, isRecording, oneShot, capturing,
    simId, setSimId, script, setScript,
    audio, elapsed, flashMarker, markerSignal,
    playSignal, stopSignal, play, stop, playing, setPlaying,
    bg, setBg, bgRef, boot,
  };

  return <RecordingContext.Provider value={value}>{children}</RecordingContext.Provider>;
}

export { SIMS };
