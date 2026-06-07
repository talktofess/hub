import type { ComponentType } from 'react';

export type SimFrame = 'phone' | 'desktop' | 'free';

export interface SimDef {
  id: string;
  label: string;
  /** Launcher glyph (emoji or letter). */
  glyph: string;
  /** Accent color used in the launcher + active state. */
  accent: string;
  frame: SimFrame;
  /** Logical render size (the sim lays out at this; the stage scales it). */
  logical: { w: number; h: number };
  /** Built and usable, vs. a roadmap placeholder. */
  ready: boolean;
  defaultScript: string;
  Component?: ComponentType;
}
