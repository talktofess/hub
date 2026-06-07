/* Zero-dependency static server for the built ./dist — the local mirror of how
   Vercel serves the hub. Use it to point OBS at http://localhost before you
   deploy (http behaves like the hosted site: real origin, reliable
   localStorage, clean URLs — unlike file://).

     node vercel-build.js && node serve.js
     # or:  npm run dev

   OBS Browser Source URL:  http://localhost:3000/#render&sim=journal           */
const http = require('http');
const fs = require('fs');
const path = require('path');

const dist = path.join(__dirname, 'dist');
const port = process.env.PORT || 3000;
const types = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.json': 'application/json',
  '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.webm': 'video/webm', '.mp4': 'video/mp4', '.mp3': 'audio/mpeg',
};

http.createServer(function (req, res) {
  let p = decodeURIComponent(req.url.split('?')[0].split('#')[0]);
  if (p === '/' || p === '') p = '/index.html';
  let file = path.join(dist, p);
  // contain to dist; map extensionless clean URLs to .html
  if (!file.startsWith(dist)) { res.writeHead(403); return res.end('forbidden'); }
  if (!fs.existsSync(file) && fs.existsSync(file + '.html')) file += '.html';
  fs.readFile(file, function (err, buf) {
    if (err) { res.writeHead(404); return res.end('not found'); }
    res.writeHead(200, {
      'Content-Type': types[path.extname(file)] || 'application/octet-stream',
      'Cache-Control': 'no-store',
    });
    res.end(buf);
  });
}).listen(port, function () {
  console.log('Sim Hub  →  http://localhost:' + port + '/');
  console.log('OBS URL  →  http://localhost:' + port + '/#render&sim=journal');
});
