/* Browser-native voiceover (Web Speech API). Zero dependencies and it works in
   the one-URL OBS flow — for OBS to record it, capture the browser source's
   audio (or desktop audio). For higher-quality TTS, swap in a server endpoint
   later; this keeps narration available everywhere with no setup. */

export interface SpeakOpts { voice?: string; rate?: number; pitch?: number; volume?: number }

function synth(): SpeechSynthesis | null {
  return typeof window !== 'undefined' && 'speechSynthesis' in window ? window.speechSynthesis : null;
}

export function listVoices(): SpeechSynthesisVoice[] {
  const s = synth();
  return s ? s.getVoices() : [];
}

/** Some browsers populate voices asynchronously; resolve once they're ready. */
export function onVoices(cb: () => void): () => void {
  const s = synth();
  if (!s) return () => {};
  cb();
  const h = () => cb();
  s.addEventListener('voiceschanged', h);
  return () => s.removeEventListener('voiceschanged', h);
}

export function speak(text: string, opts: SpeakOpts = {}): void {
  const s = synth();
  if (!s || !text.trim()) return;
  const u = new SpeechSynthesisUtterance(text);
  if (opts.voice) {
    const v = s.getVoices().find((x) => x.name === opts.voice);
    if (v) u.voice = v;
  }
  u.rate = clamp(opts.rate ?? 1, 0.5, 2);
  u.pitch = clamp(opts.pitch ?? 1, 0, 2);
  u.volume = clamp(opts.volume ?? 1, 0, 1);
  s.speak(u);
}

export function cancelSpeech(): void {
  const s = synth();
  if (s) s.cancel();
}

const clamp = (n: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, n));
