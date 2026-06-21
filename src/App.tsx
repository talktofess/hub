import { RecordingProvider } from './recording/RecordingProvider';
import { BackgroundMedia } from './recording/BackgroundMedia';
import { SyncMarker } from './recording/SyncMarker';
import { EffectsProvider } from './recording/effects/EffectsProvider';
import { SubtitleTrack } from './recording/effects/SubtitleTrack';
import { useRecording } from './recording/useRecording';
import { AppShell } from './shell/AppShell';
import './styles/global.css';

/* Bridges the audio engine into the effects layer so notifications can ding
   through the same master OBS records. */
function WithEffects() {
  const rec = useRecording();
  return (
    <EffectsProvider soundCue={() => rec.audio.cue('ding')}>
      <BackgroundMedia />
      <AppShell />
      <SubtitleTrack />
      <SyncMarker />
    </EffectsProvider>
  );
}

export default function App() {
  return (
    <RecordingProvider>
      <WithEffects />
    </RecordingProvider>
  );
}
