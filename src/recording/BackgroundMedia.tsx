import { useEffect } from 'react';
import type { CSSProperties } from 'react';
import { useRecording } from './useRecording';
import type { DisplayMode } from './settings';

/* Universal background layer behind the stage. An image renders in the chosen
   display mode (cover / contain / blur-fill / fill / tile / center) with optional
   Ken Burns; a video fills the frame (or plays audio-only); audio just plays and
   paces SRT typing. Source is a uploaded library item or a direct URL. */

function imageStyle(url: string, mode: DisplayMode): CSSProperties {
  const base: CSSProperties = { backgroundImage: `url("${url}")`, backgroundRepeat: 'no-repeat', backgroundPosition: 'center' };
  switch (mode) {
    case 'contain': return { ...base, backgroundSize: 'contain' };
    case 'fill': return { ...base, backgroundSize: '100% 100%' };
    case 'tile': return { ...base, backgroundRepeat: 'repeat', backgroundSize: 'auto' };
    case 'center': return { ...base, backgroundSize: 'auto' };
    default: return { ...base, backgroundSize: 'cover' }; // cover + blur-fill foreground
  }
}

export function BackgroundMedia() {
  const rec = useRecording();
  const { bg, bgRef, playing, isRecording } = rec;
  const active = isRecording || playing;

  useEffect(() => {
    const el = bgRef.current;
    if (!el || !bg.url || bg.kind === 'image') return;
    el.volume = bg.volume;
    el.loop = bg.loop;
    if (active) { const p = el.play(); if (p && p.catch) p.catch(() => {}); }
    else { try { el.pause(); el.currentTime = 0; } catch { /* ignore */ } }
  }, [bg.url, bg.kind, bg.volume, bg.loop, active, bgRef]);

  if (!bg.url) return <div className="bg-layer" />;

  if (bg.kind === 'image') {
    return (
      <div className="bg-layer">
        {bg.mode === 'blur-fill' && (
          <div className="bg-fill" style={{ ...imageStyle(bg.url, 'cover'), filter: 'blur(40px) brightness(0.6)', transform: 'scale(1.1)' }} />
        )}
        <div className={'bg-fill' + (bg.kenBurns ? ' ken-burns' : '')} style={imageStyle(bg.url, bg.mode)} />
      </div>
    );
  }

  // video or audio
  const isAudio = bg.kind === 'audio' || bg.audioOnly;
  const fit = bg.mode === 'fill' ? 'fill' : bg.mode === 'contain' || bg.mode === 'blur-fill' ? 'contain' : bg.mode === 'center' ? 'none' : 'cover';
  return (
    <div className="bg-layer">
      <video
        ref={bgRef}
        src={bg.url}
        playsInline
        className="bg-fill"
        style={{ objectFit: fit as any, opacity: isAudio ? 0 : 1 }}
        aria-hidden
      />
    </div>
  );
}
