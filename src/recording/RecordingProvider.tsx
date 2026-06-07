import { createContext, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode, RefObject } from 'react';
import { AudioEngine } from './audio';
import type { CaptureResult } from './audio';
import { installSeededRandom } from './rng';
import { readBootConfig } from './url';
import type { BootConfig, Mode } from './url';
import { DEFAULT_SETTINGS, loadSettings, mergeSettings, saveSettings } from './settings';
import type { BgSettings, Settings } from './settings';
import { defaultSimId, getSim } from '../sims/registry';

export interface RecordingContextValue {
  mode: Mode;
  isRecording: boolean;
  oneShot: boolean;
  capturing: boolean;
  simId: string;
  setSimId: (id: string) => void;
  script: string;
  setScript: (s: string) => void;
  settings: Settings;
  setSettings: (next: Partial<Settings>) => void;
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
  // background media (convenience view over settings.bg)
  bg: BgSettings;
  setBg: (next: Partial<BgSettings>) => void;
  bgRef: RefObject<HTMLVideoElement>;
  boot: BootConfig;
}

export const RecordingContext = createContext<RecordingContextValue | null>(null);

const scriptKey = (id: string) => 'sim:script:' + id;

function loadScript(id: string, fallback: string): string {
  try {
    const s = localStorage.getItem(scriptKey(id));
    if (s != null) return s;
  } catch { /* ignore */ }
  return fallback;
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

  // universal settings: URL (a baked OBS take) wins over saved/defaults
  const [settings, setSettingsState] = useState<Settings>(() => mergeSettings(loadSettings(), boot.settings));

  const [playing, setPlaying] = useState(false);
  const [playSignal, setPlaySignal] = useState(0);
  const [stopSignal, setStopSignal] = useState(0);
  const [markerSignal, setMarkerSignal] = useState(0);

  // apply settings to the audio engine
  useEffect(() => { audio.setProfile(settings.sound); }, [audio, settings.sound]);
  useEffect(() => { audio.setVolume(settings.volume); }, [audio, settings.volume]);
  useEffect(() => { audio.setMuted(mode === 'render'); }, [audio, mode]);

  // seeded RNG for reproducible render/audiocap passes (legacy two-pass)
  useEffect(() => {
    if (mode === 'render' || mode === 'audiocap') return installSeededRandom(simId, script);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode]);

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

  const setSettings = useCallback((next: Partial<Settings>) => {
    setSettingsState((prev) => {
      const merged = mergeSettings(prev, next);
      if (!isRecording) saveSettings(merged); // OBS context shouldn't clobber the editor's saved settings
      return merged;
    });
  }, [isRecording]);

  const setBg = useCallback((next: Partial<BgSettings>) => {
    setSettings({ bg: { ...next } as BgSettings });
  }, [setSettings]);

  const play = useCallback(() => setPlaySignal((n) => n + 1), []);
  const stop = useCallback(() => setStopSignal((n) => n + 1), []);
  const flashMarker = useCallback(() => setMarkerSignal((n) => n + 1), []);

  const elapsed = useCallback((t0: number) => {
    const a = bgRef.current;
    if (a && a.currentSrc && !a.paused && isFinite(a.currentTime)) return a.currentTime * 1000;
    return performance.now() - t0;
  }, []);

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
    settings, setSettings,
    audio, elapsed, flashMarker, markerSignal,
    playSignal, stopSignal, play, stop, playing, setPlaying,
    bg: settings.bg, setBg, bgRef, boot,
  };

  return <RecordingContext.Provider value={value}>{children}</RecordingContext.Provider>;
}

export { DEFAULT_SETTINGS };
