/* Media + config storage. Prefers Supabase Storage (cloud — works in OBS from
   any machine, full images via real https URLs); falls back to the local server
   API when Supabase isn't configured. Both expose the same interface so the rest
   of the app doesn't care which is active. */

import { BUCKET, hasSupabase, supabase } from './supabase';

export interface MediaItem {
  id: string;
  url: string;
  name: string;
  type: string;
  kind: 'image' | 'video' | 'audio';
  size: number;
  ts: number;
}

function kindOf(type: string): MediaItem['kind'] {
  if (type.startsWith('image/')) return 'image';
  if (type.startsWith('video/')) return 'video';
  if (type.startsWith('audio/')) return 'audio';
  return 'image';
}
function typeByExt(name: string): string {
  const e = name.toLowerCase().split('.').pop() || '';
  const map: Record<string, string> = {
    png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', avif: 'image/avif', svg: 'image/svg+xml',
    mp4: 'video/mp4', webm: 'video/webm', mov: 'video/quicktime', m4v: 'video/x-m4v',
    mp3: 'audio/mpeg', m4a: 'audio/mp4', wav: 'audio/wav', ogg: 'audio/ogg', aac: 'audio/aac',
  };
  return map[e] || 'application/octet-stream';
}
const uid = () => (crypto.randomUUID ? crypto.randomUUID().slice(0, 8) : Math.random().toString(36).slice(2, 10));

export async function apiAvailable(): Promise<boolean> {
  if (hasSupabase) return true;
  try { return (await fetch('/api/health', { cache: 'no-store' })).ok; } catch { return false; }
}

// ---------------- Supabase implementation ----------------
async function sbList(): Promise<MediaItem[]> {
  const { data } = await supabase!.storage.from(BUCKET).list('media', { limit: 300, sortBy: { column: 'created_at', order: 'desc' } });
  if (!data) return [];
  return data
    .filter((o) => o.name && o.name !== '.emptyFolderPlaceholder')
    .map((o) => {
      const path = 'media/' + o.name;
      const pub = supabase!.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
      const type = (o.metadata?.mimetype as string) || typeByExt(o.name);
      return { id: o.name, url: pub, name: o.name, type, kind: kindOf(type), size: (o.metadata?.size as number) || 0, ts: Date.parse(o.created_at || '') || 0 };
    });
}
async function sbUpload(file: File): Promise<MediaItem> {
  const ext = file.name.includes('.') ? file.name.split('.').pop() : (file.type.split('/')[1] || 'bin');
  const id = uid() + '.' + ext;
  const path = 'media/' + id;
  const { error } = await supabase!.storage.from(BUCKET).upload(path, file, { contentType: file.type || typeByExt(file.name), upsert: false });
  if (error) throw error;
  const pub = supabase!.storage.from(BUCKET).getPublicUrl(path).data.publicUrl;
  return { id, url: pub, name: file.name, type: file.type || typeByExt(file.name), kind: kindOf(file.type || typeByExt(file.name)), size: file.size, ts: Date.now() };
}
async function sbDelete(id: string): Promise<void> {
  try { await supabase!.storage.from(BUCKET).remove(['media/' + id]); } catch { /* ignore */ }
}
async function sbSaveConfig(cfg: unknown): Promise<string | null> {
  const token = uid();
  const blob = new Blob([JSON.stringify(cfg)], { type: 'application/json' });
  const { error } = await supabase!.storage.from(BUCKET).upload('configs/' + token + '.json', blob, { contentType: 'application/json', upsert: true });
  if (error) return null;
  return token;
}
async function sbLoadConfig(token: string): Promise<any | null> {
  try {
    const pub = supabase!.storage.from(BUCKET).getPublicUrl('configs/' + token + '.json').data.publicUrl;
    const r = await fetch(pub, { cache: 'no-store' });
    if (!r.ok) return null;
    return await r.json();
  } catch { return null; }
}

// ---------------- local server implementation ----------------
async function localList(): Promise<MediaItem[]> {
  try { const r = await fetch('/api/media', { cache: 'no-store' }); return r.ok ? await r.json() : []; } catch { return []; }
}
async function localUpload(file: File): Promise<MediaItem> {
  const r = await fetch('/api/upload', { method: 'POST', headers: { 'content-type': file.type || 'application/octet-stream', 'x-filename': file.name }, body: file });
  if (!r.ok) throw new Error('upload failed');
  return await r.json();
}
async function localDelete(id: string): Promise<void> {
  try { await fetch('/api/media/' + id, { method: 'DELETE' }); } catch { /* ignore */ }
}
async function localSaveConfig(cfg: unknown): Promise<string | null> {
  try {
    const r = await fetch('/api/config', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(cfg) });
    return r.ok ? (await r.json()).token || null : null;
  } catch { return null; }
}
async function localLoadConfig(token: string): Promise<any | null> {
  try { const r = await fetch('/api/config/' + token, { cache: 'no-store' }); return r.ok ? await r.json() : null; } catch { return null; }
}

// ---------------- unified API ----------------
export const listMedia = () => (hasSupabase ? sbList() : localList());
export const uploadMedia = (file: File) => (hasSupabase ? sbUpload(file) : localUpload(file));
export const deleteMedia = (id: string) => (hasSupabase ? sbDelete(id) : localDelete(id));
export const saveConfig = (cfg: unknown) => (hasSupabase ? sbSaveConfig(cfg) : localSaveConfig(cfg));
export const loadConfig = (token: string) => (hasSupabase ? sbLoadConfig(token) : localLoadConfig(token));
export const storageBackend = () => (hasSupabase ? 'supabase' : 'local');
