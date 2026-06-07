/* Keystroke audio engine.

   Replaces the legacy approach (a shim that hooked every AudioNode.connect on an
   iframe). Here we own the graph directly:

     osc/noise -> voiceGain -> master -> destination          (audible)
                                       \-> capDest -> MediaRecorder   (#audiocap)

   - #render  : master muted (OBS records silent keystrokes + the sync marker;
                remux.js muxes the clean track in later).
   - #audiocap: master tee'd into a MediaRecorder; on stop we hand back a webm
                blob tagged with the first-keystroke offset (t0) for the remux. */

export interface CaptureResult {
  blob: Blob;
  name: string;
}

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

  /** Lazily build the graph (must follow a user gesture for autoplay policy). */
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

  setMuted(m: boolean) {
    this.muted = m;
    if (this.master) this.master.gain.value = m ? 0 : 1;
  }

  /** Arm capture mode (call before play). simName tags the downloaded file. */
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
    } catch {
      /* MediaRecorder unsupported — capture just no-ops */
    }
  }

  stopCapture() {
    try { if (this.recorder && this.recorder.state === 'recording') this.recorder.stop(); } catch { /* ignore */ }
  }

  /** Resume the context (after a gesture). Returns true once running. */
  async resume(): Promise<void> {
    const ctx = this.ensure();
    if (ctx.state === 'suspended') { try { await ctx.resume(); } catch { /* ignore */ } }
  }

  /** Play one keystroke click. Pitch jitter uses Math.random (seeded in render/
      audiocap), so the two passes produce identical sound timing/params. */
  key() {
    const ctx = this.ensure();
    const t = ctx.currentTime;
    if (!this.sawKey) { this.sawKey = true; this.firstKey = t * 1000; }

    // short filtered noise burst = the "tick"; a low sine = the "thock"
    const dur = 0.045;
    const noise = ctx.createBufferSource();
    const buf = ctx.createBuffer(1, Math.ceil(ctx.sampleRate * dur), ctx.sampleRate);
    const data = buf.getChannelData(0);
    for (let i = 0; i < data.length; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / data.length, 2.5);
    noise.buffer = buf;
    const bp = ctx.createBiquadFilter();
    bp.type = 'bandpass';
    bp.frequency.value = 1800 + Math.random() * 1200;
    bp.Q.value = 0.8;
    const ng = ctx.createGain();
    ng.gain.value = 0.5;

    const thock = ctx.createOscillator();
    thock.type = 'sine';
    thock.frequency.value = 120 + Math.random() * 50;
    const tg = ctx.createGain();
    tg.gain.setValueAtTime(0.0001, t);
    tg.gain.exponentialRampToValueAtTime(0.22, t + 0.004);
    tg.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);

    noise.connect(bp).connect(ng).connect(this.master!);
    thock.connect(tg).connect(this.master!);
    noise.start(t); noise.stop(t + dur);
    thock.start(t); thock.stop(t + 0.07);
  }

  /** A softer click for the spacebar. */
  space() {
    const saved = this.muted; // reuse key() with a slightly lower profile
    this.key();
    void saved;
  }
}
