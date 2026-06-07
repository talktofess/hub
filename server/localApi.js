/* Local API shared by the Vite dev/preview server and the standalone server.
   No npm deps — built-in Node only. Bridges the editor browser and OBS's browser
   (separate contexts) through the local filesystem:

     POST /api/upload         raw body = file bytes; headers x-filename, content-type
                              -> saves to uploads/, returns the media item
     GET  /api/media          -> list of uploaded media
     DELETE /api/media/:id    -> remove an item
     GET  /media/:file        -> serve an uploaded file (real bytes; OBS renders
                                 complete images — no data-URL truncation)
     POST /api/config         body = JSON config -> { token }
     GET  /api/config/:token  -> the stored config JSON
     GET  /api/health         -> { ok: true }

   handleLocalApi returns true if it handled the request (response sent), else
   false so the caller serves static assets. */

import { createReadStream, existsSync, mkdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from 'fs';
import { basename, extname, join } from 'path';
import { randomUUID } from 'crypto';

const ROOT = process.cwd();
const UPLOADS = join(ROOT, 'uploads');
const CONFIG = join(ROOT, 'config');
const INDEX = join(UPLOADS, '_index.json');
mkdirSync(UPLOADS, { recursive: true });
mkdirSync(CONFIG, { recursive: true });

const MIME = {
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.gif': 'image/gif',
  '.webp': 'image/webp', '.svg': 'image/svg+xml', '.avif': 'image/avif',
  '.mp4': 'video/mp4', '.webm': 'video/webm', '.mov': 'video/quicktime', '.m4v': 'video/x-m4v',
  '.mp3': 'audio/mpeg', '.m4a': 'audio/mp4', '.wav': 'audio/wav', '.ogg': 'audio/ogg', '.aac': 'audio/aac',
};
const EXT_FOR = { 'image/png': '.png', 'image/jpeg': '.jpg', 'image/gif': '.gif', 'image/webp': '.webp', 'video/mp4': '.mp4', 'video/webm': '.webm', 'audio/mpeg': '.mp3', 'audio/mp4': '.m4a', 'audio/wav': '.wav' };

function kindOf(type) {
  if (type.startsWith('image/')) return 'image';
  if (type.startsWith('video/')) return 'video';
  if (type.startsWith('audio/')) return 'audio';
  return 'image';
}
function readIndex() {
  try { return JSON.parse(readFileSync(INDEX, 'utf8')); } catch { return []; }
}
function writeIndex(list) { writeFileSync(INDEX, JSON.stringify(list, null, 2)); }
function json(res, obj, code = 200) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(body);
  return true;
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (d) => chunks.push(d));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}
const safe = (s) => basename(String(s || '')).replace(/[^\w.\- ]+/g, '_');

export async function handleLocalApi(req, res) {
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://x').pathname); } catch { return false; }

  if (pathname.startsWith('/media/')) {
    const file = join(UPLOADS, safe(pathname.slice('/media/'.length)));
    if (!file.startsWith(UPLOADS) || !existsSync(file) || statSync(file).isDirectory()) { res.writeHead(404); res.end(); return true; }
    res.writeHead(200, { 'Content-Type': MIME[extname(file).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    createReadStream(file).pipe(res);
    return true;
  }

  if (pathname === '/api/health') return json(res, { ok: true });

  if (pathname === '/api/media' && req.method === 'GET') return json(res, readIndex());

  if (pathname === '/api/upload' && req.method === 'POST') {
    const buf = await readBody(req);
    const type = (req.headers['content-type'] || 'application/octet-stream').split(';')[0];
    const name = safe(req.headers['x-filename'] || ('upload' + (EXT_FOR[type] || '')));
    const id = randomUUID().slice(0, 8);
    const ext = extname(name) || EXT_FOR[type] || '';
    const fname = id + ext;
    writeFileSync(join(UPLOADS, fname), buf);
    const item = { id, url: '/media/' + fname, name, type, kind: kindOf(type), size: buf.length, ts: Date.now() };
    const list = readIndex(); list.unshift(item); writeIndex(list);
    return json(res, item);
  }

  if (pathname.startsWith('/api/media/') && req.method === 'DELETE') {
    const id = pathname.slice('/api/media/'.length);
    const list = readIndex();
    const item = list.find((x) => x.id === id);
    if (item) {
      try { unlinkSync(join(UPLOADS, basename(item.url))); } catch { /* ignore */ }
      writeIndex(list.filter((x) => x.id !== id));
    }
    return json(res, { ok: true });
  }

  if (pathname.startsWith('/api/config/') && req.method === 'GET') {
    const token = safe(pathname.slice('/api/config/'.length));
    const f = join(CONFIG, token + '.json');
    if (!f.startsWith(CONFIG) || !existsSync(f)) return json(res, { error: 'not found' }, 404);
    res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    res.end(readFileSync(f));
    return true;
  }

  if (pathname === '/api/config' && req.method === 'POST') {
    const buf = await readBody(req);
    let cfg;
    try { cfg = JSON.parse(buf.toString('utf8') || '{}'); } catch { return json(res, { error: 'bad json' }, 400); }
    const token = randomUUID().slice(0, 8);
    writeFileSync(join(CONFIG, token + '.json'), JSON.stringify(cfg));
    return json(res, { token });
  }

  return false;
}
