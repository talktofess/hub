/* Client helpers for the local media + config API (served by server.mjs and the
   Vite dev/preview plugin). Uploaded media lives on the local server and is
   loaded by both the editor and OBS as real files — full images, no truncation. */

export interface MediaItem {
  id: string;
  url: string; // /media/<file>
  name: string;
  type: string;
  kind: 'image' | 'video' | 'audio';
  size: number;
  ts: number;
}

let _available: boolean | null = null;

export async function apiAvailable(): Promise<boolean> {
  if (_available != null) return _available;
  try {
    const r = await fetch('/api/health', { cache: 'no-store' });
    _available = r.ok;
  } catch { _available = false; }
  return _available;
}

export async function listMedia(): Promise<MediaItem[]> {
  try {
    const r = await fetch('/api/media', { cache: 'no-store' });
    if (!r.ok) return [];
    return await r.json();
  } catch { return []; }
}

export async function uploadMedia(file: File): Promise<MediaItem> {
  const r = await fetch('/api/upload', {
    method: 'POST',
    headers: { 'content-type': file.type || 'application/octet-stream', 'x-filename': file.name },
    body: file,
  });
  if (!r.ok) throw new Error('upload failed');
  return await r.json();
}

export async function deleteMedia(id: string): Promise<void> {
  try { await fetch('/api/media/' + id, { method: 'DELETE' }); } catch { /* ignore */ }
}

export async function saveConfig(cfg: unknown): Promise<string | null> {
  try {
    const r = await fetch('/api/config', {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(cfg),
    });
    if (!r.ok) return null;
    const j = await r.json();
    return j.token || null;
  } catch { return null; }
}

export async function loadConfig(token: string): Promise<any | null> {
  try {
    const r = await fetch('/api/config/' + token, { cache: 'no-store' });
    if (!r.ok) return null;
    return await r.json();
  } catch { return null; }
}

/** Resolve a background setting to a playable URL (library id or direct url). */
export function resolveMediaUrl(mediaId: string | null, url: string | null, lib: MediaItem[]): string | null {
  if (mediaId) {
    const m = lib.find((x) => x.id === mediaId);
    if (m) return m.url;
  }
  return url;
}
