import type { ComponentType } from 'react';

export type SimFrame = 'phone' | 'desktop' | 'free';

export interface SimLogical { w: number; h: number }

/** A sim-specific tab in the settings drawer. The Panel reads/writes the sim's
    own settings via useSimSettings(); it renders below the universal tabs. */
export interface SimTab {
  id: string;
  label: string;
  Panel: ComponentType;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export interface SimDef<S extends object = any> {
  id: string;
  label: string;
  /** Launcher glyph (emoji or letter). */
  glyph: string;
  /** Accent color used in the launcher + active state. */
  accent: string;
  frame: SimFrame;
  /** Logical render size (the sim lays out at this; the stage scales it). */
  logical: SimLogical;
  /** Built and usable, vs. a roadmap placeholder. */
  ready: boolean;
  defaultScript: string;
  Component?: ComponentType;
  /** Per-sim settings defaults (the shape the sim's tabs read/write). */
  defaultSettings?: S;
  /** Extra drawer tabs contributed by this sim. */
  settingsTabs?: SimTab[];
  /** Optional dynamic logical size from the sim's own settings (e.g. an email
      that switches between desktop landscape and reel portrait). */
  getLogical?: (s: S) => SimLogical;
}

/* Convention: if a sim's effective settings include a `sound` (SoundProfile)
   and/or `soundVolume` (0..1) key, they override the universal keystroke sound
   while that sim is active (e.g. journal → pencil, typewriter → typewriter).
   The provider resolves this; sims expose it through their own Sound tab. */
