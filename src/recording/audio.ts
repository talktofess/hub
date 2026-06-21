/* Keystroke audio engine with selectable sound profiles.

   Graph:  per-key nodes -> master -> destination          (audible, OBS records)
   Single-take recording: keystroke audio plays through the browser source and
   OBS captures it directly — one URL, one pass. (master can still be muted; the
   deterministic two-pass path lives in legacy/.) */

import type { SoundProfile } from './settings';

export interface CaptureResult { blob: Blob; name: string }

export class AudioEngine {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private capDest: MediaStreamAudioDestinationNode | null = null;
  private recorder: MediaRecorder | null = null;
  private chunks: Blob[] = [];
  private recStart = 0;
  private firstKey = 0;
  private sawKey = false;
  private capturing = false;
  private muted = false;
  private simName = 'sim';
  private onCapture: ((r: CaptureResult) => void) | null = null;

  profile: SoundProfile = 'mechanical';
  volume = 0.85;

  private ensure(): AudioContext {
    if (this.ctx) return this.ctx;
    const AC = window.AudioContext || (window as any).webkitAudioContext;
    const ctx: AudioContext = new AC();
    const master = ctx.createGain();
    master.gain.value = this.muted ? 0 : 1;
    master.connect(ctx.destination);
    this.ctx = ctx;
    this.master = master;
    if (this.capturing) this.attachCapture();
    return ctx;
  }

  setProfile(p: SoundProfile) { this.profile = p; }
  setVolume(v: number) { this.volume = v; }
  setMuted(m: boolean) { this.muted = m; if (this.master) this.master.gain.value = m ? 0 : 1; }

  armCapture(simName: string, onCapture: (r: CaptureResult) => void) {
    this.capturing = true;
    this.simName = simName;
    this.onCapture = onCapture;
    this.sawKey = false;
    if (this.ctx) this.attachCapture();
  }

  private attachCapture() {
    if (!this.ctx || !this.master || this.capDest) return;
    try {
      this.capDest = this.ctx.createMediaStreamDestination();
      this.master.connect(this.capDest);
      this.recorder = new MediaRecorder(this.capDest.stream, { mimeType: 'audio/webm;codecs=opus' });
      this.chunks = [];
      this.recorder.ondataavailable = (e) => { if (e.data && e.data.size) this.chunks.push(e.data); };
      this.recorder.onstop = () => {
        const blob = new Blob(this.chunks, { type: 'audio/webm' });
        const t0 = Math.max(0, Math.round(this.firstKey - this.recStart));
        this.onCapture?.({ blob, name: `${this.simName}__t0-${t0}ms.webm` });
      };
      this.recStart = (this.ctx.currentTime || 0) * 1000;
      this.recorder.start();
    } catch { /* unsupported — no-op */ }
  }

  stopCapture() {
    try { if (this.recorder && this.recorder.state === 'recording') this.recorder.stop(); } catch { /* ignore */ }
  }

  async resume(): Promise<void> {
    const ctx = this.ensure();
    if (ctx.state === 'suspended') { try { await ctx.resume(); } catch { /* ignore */ } }
  }

  /** Play one keystroke using the active profile. Jitter uses Math.random
      (seeded in render/audiocap) so repeated takes are identical. */
  key() {
    if (this.profile === 'none' || this.volume <= 0) return;
    const ctx = this.ensure();
    const t = ctx.currentTime;
    if (!this.sawKey) { this.sawKey = true; this.firstKey = t * 1000; }
    const v = this.volume;
    switch (this.profile) {
      case 'typewriter': return this.synthTypewriter(ctx, t, v);
      case 'soft': return this.synthSoft(ctx, t, v);
      case 'tactile': return this.synthTactile(ctx, t, v);
      case 'blue': return this.synthBlue(ctx, t, v);
      case 'vintage': return this.synthVintage(ctx, t, v);
      case 'bubble': return this.synthBubble(ctx, t, v);
      case 'mush': return this.synthMush(ctx, t, v);
      case 'pencil': return this.synthPencil(ctx, t, v);
      default: return this.synthMechanical(ctx, t, v);
    }
  }

  space() { this.key(); }

  /** A one-off non-keystroke sound (carriage return ding, page turn, …) used by
      sims for moments that aren't a letter. Routed through the same master so
      OBS records it with everything else. */
  cue(name: 'return' | 'ding' | 'space') {
    if (this.volume <= 0) return;
    const ctx = this.ensure();
    const t = ctx.currentTime;
    if (name === 'return' || name === 'ding') this.synthCarriageReturn(ctx, t, this.volume, name === 'return');
    else this.synthSoft(ctx, t, this.volume);
  }

  // ---- profiles ----
  private noise(ctx: AudioContext, dur: number, decay: number): AudioBufferSourceNode {
    const src = ctx.createBufferSource();
    const buf = ctx.createBuffer(1, Math.max(1, Math.ceil(ctx.sampleRate * dur)), ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, decay);
    src.buffer = buf;
    return src;
  }

  private synthMechanical(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const dur = 0.045;
    const n = this.noise(ctx, dur, 2.5);
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 1800 + Math.random() * 1200; bp.Q.value = 0.8;
    const ng = ctx.createGain(); ng.gain.value = 0.5 * v;
    const osc = ctx.createOscillator(); osc.type = 'sine'; osc.frequency.value = 120 + Math.random() * 50;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.22 * v, t + 0.004); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);
    n.connect(bp).connect(ng).connect(m); osc.connect(og).connect(m);
    n.start(t); n.stop(t + dur); osc.start(t); osc.stop(t + 0.07);
  }

  private synthTypewriter(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const n = this.noise(ctx, 0.03, 4);
    const hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 2600;
    const ng = ctx.createGain(); ng.gain.value = 0.55 * v;
    const clack = ctx.createOscillator(); clack.type = 'square'; clack.frequency.value = 180 + Math.random() * 40;
    const cg = ctx.createGain();
    cg.gain.setValueAtTime(0.0001, t); cg.gain.exponentialRampToValueAtTime(0.18 * v, t + 0.003); cg.gain.exponentialRampToValueAtTime(0.0001, t + 0.05);
    const ring = ctx.createOscillator(); ring.type = 'triangle'; ring.frequency.value = 2400 + Math.random() * 400;
    const rg = ctx.createGain();
    rg.gain.setValueAtTime(0.0001, t); rg.gain.exponentialRampToValueAtTime(0.05 * v, t + 0.002); rg.gain.exponentialRampToValueAtTime(0.0001, t + 0.04);
    n.connect(hp).connect(ng).connect(m); clack.connect(cg).connect(m); ring.connect(rg).connect(m);
    n.start(t); n.stop(t + 0.03); clack.start(t); clack.stop(t + 0.05); ring.start(t); ring.stop(t + 0.04);
  }

  private synthSoft(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const n = this.noise(ctx, 0.05, 2);
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 500 + Math.random() * 150;
    const ng = ctx.createGain(); ng.gain.value = 0.28 * v;
    const osc = ctx.createOscillator(); osc.type = 'sine'; osc.frequency.value = 85 + Math.random() * 30;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.16 * v, t + 0.006); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.07);
    n.connect(lp).connect(ng).connect(m); osc.connect(og).connect(m);
    n.start(t); n.stop(t + 0.05); osc.start(t); osc.stop(t + 0.08);
  }

  private synthTactile(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const n = this.noise(ctx, 0.04, 3);
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 1100 + Math.random() * 300;
    const ng = ctx.createGain(); ng.gain.value = 0.32 * v;
    const osc = ctx.createOscillator(); osc.type = 'sine'; osc.frequency.value = 95 + Math.random() * 25;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.3 * v, t + 0.005); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.085);
    n.connect(lp).connect(ng).connect(m); osc.connect(og).connect(m);
    n.start(t); n.stop(t + 0.04); osc.start(t); osc.stop(t + 0.09);
  }

  // sharp double-tick (key down + up) of a clicky blue switch
  private synthBlue(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const tick = (at: number, amp: number) => {
      const n = this.noise(ctx, 0.02, 5);
      const hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 3200;
      const g = ctx.createGain(); g.gain.value = amp * v;
      n.connect(hp).connect(g).connect(m); n.start(at); n.stop(at + 0.02);
    };
    tick(t, 0.6);
    tick(t + 0.035 + Math.random() * 0.01, 0.4);
    const osc = ctx.createOscillator(); osc.type = 'square'; osc.frequency.value = 240;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.1 * v, t + 0.002); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.03);
    osc.connect(og).connect(m); osc.start(t); osc.stop(t + 0.03);
  }

  // woody, lower vintage keyboard clack
  private synthVintage(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const n = this.noise(ctx, 0.05, 2.2);
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 750 + Math.random() * 250; bp.Q.value = 1.1;
    const ng = ctx.createGain(); ng.gain.value = 0.45 * v;
    const osc = ctx.createOscillator(); osc.type = 'triangle'; osc.frequency.value = 150 + Math.random() * 40;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.24 * v, t + 0.006); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.09);
    n.connect(bp).connect(ng).connect(m); osc.connect(og).connect(m);
    n.start(t); n.stop(t + 0.05); osc.start(t); osc.stop(t + 0.1);
  }

  // playful pitched pop
  private synthBubble(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const osc = ctx.createOscillator(); osc.type = 'sine';
    const f0 = 420 + Math.random() * 180;
    osc.frequency.setValueAtTime(f0, t); osc.frequency.exponentialRampToValueAtTime(f0 * 2.2, t + 0.05);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.3 * v, t + 0.008); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.09);
    osc.connect(g).connect(m); osc.start(t); osc.stop(t + 0.1);
  }

  // graphite pencil — a short gritty scratch of filtered noise with a soft
  // paper-drag body. Length + brightness vary per letter so it reads as a hand
  // moving, not a click.
  private synthPencil(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const dur = 0.05 + Math.random() * 0.05;
    const n = this.noise(ctx, dur, 1.3);
    const bp = ctx.createBiquadFilter(); bp.type = 'bandpass';
    bp.frequency.value = 2200 + Math.random() * 2600; bp.Q.value = 0.6;
    const ng = ctx.createGain();
    ng.gain.setValueAtTime(0.0001, t);
    ng.gain.exponentialRampToValueAtTime(0.16 * v, t + 0.008);
    ng.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    // a little low paper-body rumble under the scratch
    const body = this.noise(ctx, dur, 2);
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 420 + Math.random() * 120;
    const bg = ctx.createGain(); bg.gain.value = 0.06 * v;
    n.connect(bp).connect(ng).connect(m);
    body.connect(lp).connect(bg).connect(m);
    n.start(t); n.stop(t + dur); body.start(t); body.stop(t + dur);
  }

  // typewriter carriage return — a metallic bell ding plus a quick return swipe.
  private synthCarriageReturn(ctx: AudioContext, t: number, v: number, withSwipe: boolean) {
    const m = this.master!;
    // bell ding: two close partials, fast attack, long ring
    const ding = (freq: number, amp: number) => {
      const osc = ctx.createOscillator(); osc.type = 'sine'; osc.frequency.value = freq;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(amp * v, t + 0.004);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.6);
      osc.connect(g).connect(m); osc.start(t); osc.stop(t + 0.62);
    };
    ding(1850, 0.18);
    ding(2640, 0.10);
    if (withSwipe) {
      // return swipe: a band of noise that sweeps as the carriage flies back
      const dur = 0.16;
      const n = this.noise(ctx, dur, 1.1);
      const bp = ctx.createBiquadFilter(); bp.type = 'bandpass'; bp.Q.value = 0.9;
      bp.frequency.setValueAtTime(900, t + 0.04);
      bp.frequency.exponentialRampToValueAtTime(3200, t + 0.04 + dur);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t + 0.04);
      g.gain.exponentialRampToValueAtTime(0.12 * v, t + 0.07);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.04 + dur);
      n.connect(bp).connect(g).connect(m); n.start(t + 0.04); n.stop(t + 0.04 + dur);
    }
  }

  // very muted marshmallow thud
  private synthMush(ctx: AudioContext, t: number, v: number) {
    const m = this.master!;
    const n = this.noise(ctx, 0.05, 1.6);
    const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 320 + Math.random() * 80;
    const ng = ctx.createGain(); ng.gain.value = 0.2 * v;
    const osc = ctx.createOscillator(); osc.type = 'sine'; osc.frequency.value = 70 + Math.random() * 20;
    const og = ctx.createGain();
    og.gain.setValueAtTime(0.0001, t); og.gain.exponentialRampToValueAtTime(0.12 * v, t + 0.01); og.gain.exponentialRampToValueAtTime(0.0001, t + 0.08);
    n.connect(lp).connect(ng).connect(m); osc.connect(og).connect(m);
    n.start(t); n.stop(t + 0.05); osc.start(t); osc.stop(t + 0.09);
  }
}
