import { useContext } from 'react';
import { RecordingContext } from './RecordingProvider';
import type { RecordingContextValue } from './RecordingProvider';

export function useRecording(): RecordingContextValue {
  const ctx = useContext(RecordingContext);
  if (!ctx) throw new Error('useRecording must be used within <RecordingProvider>');
  return ctx;
}
