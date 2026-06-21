import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';

/* Reusable recording effects shared by every sim — notifications, a camera
   (zoom that can PERSIST), a fake cursor, a lens magnifier, on-screen
   annotations, and subtitles. All positions are normalized 0..1 over the sim's
   logical canvas, so they line up at any layout/resolution.

   Two contexts on purpose: the CONTROLLER is stable (functions only), so a sim
   can fire effects from inside its typing plan without re-rendering itself; the
   STATE changes and is consumed only by the overlay layer in the Stage. */

export interface Toast {
  id: number;
  from: string;
  subject: string;
  body?: string;
  icon?: string;
  accent?: string;
}

export interface CameraTarget {
  z: number;   // scale
  cx: number;  // focus point x (0..1)
  cy: number;  // focus point y (0..1)
  ms: number;  // transition duration
}

export interface CursorState {
  x: number; y: number; // 0..1
  ms: number;
  visible: boolean;
  down: boolean;
}

export interface LensState {
  x: number; y: number; // 0..1
  r: number;            // spotlight radius as fraction of width
  mag?: number;         // reserved (magnification); spotlight ignores it
}

export type Annotation =
  | { id: number; kind: 'string'; from: [number, number]; to: [number, number]; color: string; curve: number; width: number; label?: string }
  | { id: number; kind: 'arrow'; from: [number, number]; to: [number, number]; color: string; width: number; label?: string }
  | { id: number; kind: 'box'; rect: [number, number, number, number]; color: string; width: number; label?: string };

export interface EffectsState {
  toasts: Toast[];
  camera: CameraTarget;
  cursor: CursorState;
  lens: LensState | null;
  annotations: Annotation[];
  subtitle: string | null;
}

export interface EffectsController {
  resetAll(): void;
  notify(t: Omit<Toast, 'id'>, opts?: { ms?: number; sound?: boolean }): void;
  clearToasts(): void;
  setCamera(target: Partial<CameraTarget> | null, ms?: number): void;
  cursorTo(x: number, y: number, ms?: number): void;
  cursorClick(): void;
  cursorShow(visible: boolean): void;
  setLens(l: LensState | null): void;
  setAnnotations(list: Annotation[]): void;
  addAnnotation(a: Omit<Annotation, 'id'>): void;
  clearAnnotations(): void;
  setSubtitle(text: string | null): void;
}

const RESET_CAMERA: CameraTarget = { z: 1, cx: 0.5, cy: 0.5, ms: 600 };
const RESET_CURSOR: CursorState = { x: 0.5, y: 0.6, ms: 500, visible: false, down: false };

const INITIAL: EffectsState = {
  toasts: [], camera: RESET_CAMERA, cursor: RESET_CURSOR, lens: null, annotations: [], subtitle: null,
};

const StateCtx = createContext<EffectsState>(INITIAL);
const CtrlCtx = createContext<EffectsController | null>(null);

export function useEffectsState() { return useContext(StateCtx); }
export function useEffects(): EffectsController {
  const c = useContext(CtrlCtx);
  if (!c) throw new Error('useEffects outside EffectsProvider');
  return c;
}

export function EffectsProvider({ children, soundCue }: { children: ReactNode; soundCue?: (name: 'ding') => void }) {
  const [state, setState] = useState<EffectsState>(INITIAL);
  const idRef = useRef(1);
  const timersRef = useRef<Record<number, ReturnType<typeof setTimeout>>>({});

  const dismiss = useCallback((id: number) => {
    setState((s) => ({ ...s, toasts: s.toasts.filter((t) => t.id !== id) }));
    const tm = timersRef.current[id];
    if (tm) { clearTimeout(tm); delete timersRef.current[id]; }
  }, []);

  const controller = useMemo<EffectsController>(() => ({
    resetAll() {
      Object.values(timersRef.current).forEach(clearTimeout);
      timersRef.current = {};
      setState(INITIAL);
    },
    notify(t, opts) {
      const id = idRef.current++;
      setState((s) => ({ ...s, toasts: [...s.toasts, { ...t, id }] }));
      if (opts?.sound && soundCue) soundCue('ding');
      const ms = opts?.ms ?? 4200;
      timersRef.current[id] = setTimeout(() => dismiss(id), ms);
    },
    clearToasts() {
      Object.values(timersRef.current).forEach(clearTimeout);
      timersRef.current = {};
      setState((s) => ({ ...s, toasts: [] }));
    },
    setCamera(target, ms) {
      setState((s) => ({
        ...s,
        camera: target == null
          ? { ...RESET_CAMERA, ms: ms ?? RESET_CAMERA.ms }
          : { ...s.camera, ...target, ms: ms ?? target.ms ?? s.camera.ms },
      }));
    },
    cursorTo(x, y, ms) {
      setState((s) => ({ ...s, cursor: { ...s.cursor, x, y, ms: ms ?? 500, visible: true } }));
    },
    cursorClick() {
      setState((s) => ({ ...s, cursor: { ...s.cursor, down: true } }));
      setTimeout(() => setState((s) => ({ ...s, cursor: { ...s.cursor, down: false } })), 160);
    },
    cursorShow(visible) { setState((s) => ({ ...s, cursor: { ...s.cursor, visible } })); },
    setLens(l) { setState((s) => ({ ...s, lens: l })); },
    setAnnotations(list) { setState((s) => ({ ...s, annotations: list })); },
    addAnnotation(a) { setState((s) => ({ ...s, annotations: [...s.annotations, { ...a, id: idRef.current++ } as Annotation] })); },
    clearAnnotations() { setState((s) => ({ ...s, annotations: [] })); },
    setSubtitle(text) { setState((s) => ({ ...s, subtitle: text })); },
  }), [dismiss, soundCue]);

  return (
    <CtrlCtx.Provider value={controller}>
      <StateCtx.Provider value={state}>{children}</StateCtx.Provider>
    </CtrlCtx.Provider>
  );
}
