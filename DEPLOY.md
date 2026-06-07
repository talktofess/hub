# Deploying the Sim Hub to Vercel

The hub is a single self-contained file (`hub.html`) that `build-hub.js` produces
by embedding all 9 sims. There is **no server, no API, nothing dynamic** — so on
Vercel it's a plain static deployment. Only the **hub** is deployed; the
individual sim files (`email.html`, `journal.html`, …) are build-time *sources*
that get baked into the hub, not separate pages.

Serving the hub over **https** is strictly better than the old `file://` setup:
`MediaRecorder` (the clean-audio pass) and `navigator.clipboard` (the 📋 OBS URL
button) both require a *secure context*, which https satisfies and `file://`
does not reliably.

## What was added

| File              | Purpose |
|-------------------|---------|
| `vercel.json`     | Build command + static output config + `no-store` headers (OBS always gets the freshest hub). |
| `vercel-build.js` | Runs `build-hub.js`, then stages the hub into `dist/` as `index.html` **and** `hub.html`. |
| `package.json`    | `npm run build` (what Vercel runs) and `npm run dev` (build + local server). |
| `serve.js`        | Zero-dependency local static server — mirrors how Vercel serves, for testing OBS over `http://localhost`. |
| `.vercelignore`   | Keeps the upload lean (excludes `raw/`, media, the Remotion `template/`, etc.). |

`build-hub.js` and the sim sources are unchanged — the local workflow still works.

## Deploy

From this folder (`1v0`):

```
npm i -g vercel        # once
vercel                 # preview deploy (answer the prompts)
vercel --prod          # production deploy
```

Vercel auto-detects `vercel.json`: it runs `node vercel-build.js` and serves
`dist/`. No framework preset, no env vars needed. The hub lands at:

```
https://<your-app>.vercel.app/            # = index.html
https://<your-app>.vercel.app/hub.html    # same bytes (old-style URLs keep working)
```

(Or connect this folder to a Git repo and import it at vercel.com — same config.)

## Test locally first (the way OBS will hit it)

```
npm run dev
# Sim Hub  →  http://localhost:3000/
# OBS URL  →  http://localhost:3000/#render&sim=journal
```

`http://localhost` is a secure context too, so the audio pass works locally.

## Point OBS at the deployed hub

Regenerate the OBS scene collection against your deployment, then re-import
`Sim Hub.json` (Scene Collection → Import):

```powershell
# PowerShell (Windows)
$env:HUB_URL='https://your-app.vercel.app/'; node build-obs-scenes.js
```
```bash
# bash
HUB_URL=https://your-app.vercel.app/ node build-obs-scenes.js
```

Without `HUB_URL` it defaults to `http://localhost:3000/` for local testing.

The recording flow in `RENDER.md` is unchanged — just swap the
`file:///A:/…/hub.html` URLs for `https://your-app.vercel.app/`:

- **Video pass (OBS):** browser sources at `…/#present&sim=NAME` (or `#render&…`
  for the deterministic muted-keystroke pass).
- **Audio pass:** open `https://your-app.vercel.app/#audiocap&sim=NAME` in a
  normal browser; it downloads the clean `NAME__t0-<ms>ms.webm`.
- **Mux:** `node remux.js NAME.mkv NAME__t0-…ms.webm` — runs locally with ffmpeg,
  exactly as before.

## Notes

- **Background media for recording must be a hosted URL** (paste a direct
  `https://…` link in the 🎵 BG panel). Locally-picked files (`blob:`) don't
  survive a reload and won't reach a hosted OBS source — that was already true on
  `file://`.
- A full-quality **journal doodle** ships if you drop `doodle.png` next to the
  hub before building (it's copied into `dist/`); otherwise paste/inline the
  doodle as usual.
- The hub is ~1.9 MB. With `no-store`, OBS re-downloads it each refresh — fine on
  a LAN/broadband recording setup and guarantees you never record a stale build.
