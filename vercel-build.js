/* Vercel build step for the Sim Hub.

   The hub is a single self-contained file (`hub.html`) that build-hub.js
   produces by embedding all 9 sims. Vercel just needs to serve that one file
   statically — there is no server, no API, nothing dynamic. This script:

     1. runs build-hub.js (so the deployed hub is always rebuilt from the
        current sim sources — the .html files are the source of truth), and
     2. stages the result into ./dist as both index.html and hub.html.

   `index.html`  -> the hub at the site root (https://<app>.vercel.app/)
   `hub.html`    -> same bytes, so existing #render / #present / #audiocap URLs
                    that end in /hub.html keep working unchanged.

   Run locally exactly as Vercel does:  node vercel-build.js                    */
const fs = require('fs');
const path = require('path');

// Executes build-hub.js (top-level script) — writes hub.html next to it.
require('./build-hub.js');

const dir = __dirname;
const dist = path.join(dir, 'dist');
fs.mkdirSync(dist, { recursive: true });

const hub = fs.readFileSync(path.join(dir, 'hub.html'));
fs.writeFileSync(path.join(dist, 'index.html'), hub);
fs.writeFileSync(path.join(dist, 'hub.html'), hub);

// Optional: a full-quality journal doodle placed next to the hub ships too, so
// the journal sim's __HUB_DIR__ image loader resolves it over https in OBS.
for (const f of ['doodle.png', 'doodle.jpg', 'doodle.jpeg']) {
  const p = path.join(dir, f);
  if (fs.existsSync(p)) { fs.copyFileSync(p, path.join(dist, f)); console.log('  staged ' + f); }
}

console.log(`\nStaged dist/ for Vercel  (index.html + hub.html, ${(hub.length / 1024 / 1024).toFixed(2)} MB each)`);
