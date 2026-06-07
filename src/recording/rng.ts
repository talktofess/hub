/* Deterministic RNG — the property the OBS render pipeline depends on:
   same (sim + script) ⇒ identical take, so the muted video pass (#render) and
   the clean-audio pass (#audiocap) line up frame-for-frame. Ported verbatim from
   the legacy hub shim so existing takes reproduce. */

export function fnv(s: string): number {
  let h = 2166136261 >>> 0;
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function mulberry32(a: number): () => number {
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function seedFor(sim: string, script: string): number {
  return (fnv(sim + ' ' + (script || '')) ^ 0x9e3779b9) >>> 0;
}

/* Replace the global Math.random with a seeded stream. Called once at boot in
   the render/audiocap passes so every source of jitter (typing cadence,
   keystroke pitch) is reproducible across the two passes. Returns a restore fn. */
export function installSeededRandom(sim: string, script: string): () => void {
  const original = Math.random;
  const rng = mulberry32(seedFor(sim, script));
  Math.random = rng;
  return () => {
    Math.random = original;
  };
}
