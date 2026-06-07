import { useEffect, useState } from 'react';
import { useRecording } from '../recording/useRecording';
import { apiAvailable, deleteMedia, listMedia, uploadMedia } from '../recording/media';
import type { MediaItem } from '../recording/media';

/* Universal media library — uploads go to the local server and are loaded by both
   the editor and OBS as real files (full images, no truncation). Pick one as the
   background; choose its display mode below. */
export function MediaLibrary() {
  const rec = useRecording();
  const [items, setItems] = useState<MediaItem[]>([]);
  const [ok, setOk] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => listMedia().then(setItems);
  useEffect(() => { apiAvailable().then((a) => { setOk(a); if (a) refresh(); }); }, []);

  const onFiles = async (files: FileList | null) => {
    if (!files || !files.length) return;
    setBusy(true);
    for (const f of Array.from(files)) { try { await uploadMedia(f); } catch { /* ignore */ } }
    await refresh();
    setBusy(false);
  };

  const pick = (m: MediaItem) => rec.setBg({ mediaId: m.id, url: m.url, kind: m.kind });
  const remove = async (m: MediaItem) => {
    await deleteMedia(m.id);
    if (rec.bg.mediaId === m.id) rec.setBg({ mediaId: null, url: null });
    refresh();
  };

  if (ok === false) {
    return (
      <p className="hint">
        Uploads need a storage backend. Either set Supabase env vars
        (<code>VITE_SUPABASE_URL</code> / <code>VITE_SUPABASE_ANON_KEY</code> — see
        <code> SUPABASE.md</code>), or run the local server (<code>npm run local</code>). You can
        still paste a direct URL below.
      </p>
    );
  }

  return (
    <div>
      <label className="uploader">
        <input type="file" accept="image/*,video/*,audio/*" multiple style={{ display: 'none' }}
          onChange={(e) => onFiles(e.target.files)} />
        {busy ? 'Uploading…' : '＋ Upload images / videos / audio'}
      </label>
      {items.length > 0 && (
        <div className="media-grid">
          {items.map((m) => (
            <div key={m.id} className={'media-cell' + (rec.bg.mediaId === m.id ? ' sel' : '')} onClick={() => pick(m)} title={m.name}>
              {m.kind === 'image' ? <img src={m.url} alt="" />
                : m.kind === 'video' ? <video src={m.url} muted />
                : <div className="media-audio">♪</div>}
              <button className="media-del" onClick={(e) => { e.stopPropagation(); remove(m); }} title="Delete">×</button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
