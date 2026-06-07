import { RecordingProvider } from './recording/RecordingProvider';
import { BackgroundMedia } from './recording/BackgroundMedia';
import { SyncMarker } from './recording/SyncMarker';
import { AppShell } from './shell/AppShell';
import './styles/global.css';

export default function App() {
  return (
    <RecordingProvider>
      <BackgroundMedia />
      <AppShell />
      <SyncMarker />
    </RecordingProvider>
  );
}
