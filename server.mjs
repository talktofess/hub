/* Standalone local server for OBS recording: serves the built ./dist plus the
   local media + config API. Point OBS (same machine) at http://localhost:3000.

     npm run build && node server.mjs      (or: npm run local)

   Uploaded media is served as real files from /media/* so OBS renders complete
   images — no data-URL truncation. */
import { createServer } from 'http';
import { createReadStream, existsSync, statSync } from 'fs';
import { extname, join } from 'path';
import { handleLocalApi } from './server/localApi.js';

const DIST = join(process.cwd(), 'dist');
const PORT = process.env.PORT || 3000;
const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css',
  '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.woff2': 'font/woff2', '.ico': 'image/x-icon',
};

if (!existsSync(DIST)) {
  console.error('No dist/ — run `npm run build` first.');
  process.exit(1);
}

createServer(async (req, res) => {
  try { if (await handleLocalApi(req, res)) return; } catch (e) { res.writeHead(500); return res.end(String(e)); }
  let p;
  try { p = decodeURIComponent(new URL(req.url, 'http://x').pathname); } catch { p = '/'; }
  if (p === '/' || p === '') p = '/index.html';
  let f = join(DIST, p);
  if (!f.startsWith(DIST)) { res.writeHead(403); return res.end(); }
  if (!existsSync(f) || statSync(f).isDirectory()) f = join(DIST, 'index.html'); // SPA fallback
  res.writeHead(200, { 'Content-Type': MIME[extname(f).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store' });
  createReadStream(f).pipe(res);
}).listen(PORT, () => {
  console.log('Sim Hub  →  http://localhost:' + PORT + '/');
  console.log('OBS URL  →  http://localhost:' + PORT + '/   (copy the in-app OBS URL for a take)');
});
