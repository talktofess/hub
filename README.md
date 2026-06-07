# Sim Hub

Typed-animation recreations of everyday apps (Notes, iMessage, Email, …) for
screen-recording short videos. Built as a **Vite + React** app and deployed
static on **Vercel**; designed to run as an **OBS Browser Source**.

This is a ground-up rebuild for a more app-like feel. The previous
single-file/iframe version lives in [`legacy/`](./legacy) as reference (its
deterministic-render notes are in [`legacy/RENDER.md`](./legacy/RENDER.md)).

## Run it

```bash
npm install
npm run dev        # http://localhost:3000  (edit, with HMR + media/config API)
npm run local      # build + node server.mjs — what you point OBS at for recording
npm run build      # -> dist/  (what Vercel serves)
npm run typecheck
```

For recording, run `npm run local` and point OBS (same machine) at
`http://localhost:3000`. That server also serves uploaded media and the config
store the OBS URL relies on.

## How it works

- **Edit mode** (`/`): a launcher (left), the live device stage (center), and a
  control panel (right) — script editor, ▶ Preview, background media, and
  "Copy OBS URL" buttons.
- The stage renders each sim at a fixed **logical size** (1080×1920) and scales
  it to fit, so it looks identical at any OBS source resolution.
- A script is typed character-by-character with **keystroke audio**. Body text
  supports the typo-and-correct markup `[[wrong|right]]` — types the wrong
  spelling, backspaces, types the correction.

### Recording (OBS) — one URL

**Copy OBS URL** builds a single link you paste **once** into an OBS Browser
Source. OBS records the take directly (video + keystroke audio together). The
heavy stuff doesn't go in the URL — the full config (script + all universal
settings + chosen media) is stored under a short token, so the URL stays tiny:

```
http://localhost:3000/#present&sim=notes&cfg=ab12cd34
```

OBS fetches the config by token from the local server. If the server isn't
running, the button falls back to lightweight inline params (no media).

### Universal settings (gear → drawer)

Global — the same for **every** sim, baked into the one URL:

- **Typing sound** — 8 profiles (mechanical, blue switch, typewriter, vintage,
  tactile, soft, marshmallow, bubble) + volume, with a Test button
- **Speed & realism** — speed, hesitation, timing jitter, auto-mistakes
  (fumble + self-correct), start delay, loop + hold
- **Caret** — show/blink/color/style (bar / block / underline)
- **Look & feel** — theme, accent, text scale, grain, vignette
- **Background media** — see below

### Media (universal, uploaded — not in the URL)

Big media can't ride in the URL (that's what truncated images in OBS). Instead,
**upload** images/videos/audio in the settings drawer; they're stored in
**Supabase Storage** and loaded by both the editor and OBS as **real https
files** — complete images, no truncation, and it works from any machine + the
hosted Vercel app. Set up once: see [`SUPABASE.md`](./SUPABASE.md) (reuses the
shared vault/inkwell project, its own `hub-media` bucket). The config token is
stored there too, so `…#present&sim=NAME&cfg=TOKEN` resolves from the cloud.

Without Supabase env vars, it falls back to the **local server** (`npm run
local`, serves `uploads/` over `http://localhost`).

Pick a media item as the background and choose a **display mode**: cover /
contain / blur-fill / stretch / tile / center, with optional **Ken Burns**
pan-zoom; a video can play as video or **audio-only**.

> The legacy deterministic two-pass flow (`#render` + `#audiocap`, mux via
> `legacy/remux.js`) is still parsed for backward compatibility.

## Architecture

```
src/
  recording/     deterministic RNG, URL/mode parsing, keystroke audio engine,
                 typing engine, provider/hook, sync marker, background media
  sims/          one folder per sim + a registry; types.ts is the sim contract
  shell/         launcher, stage (resolution-independent scaling), control panel
```

Adding a sim = a folder under `src/sims/` exporting a `SimDef` (id, label, glyph,
logical size, default script, component), registered in `src/sims/registry.ts`.
The component drives the shared typing engine via `useTypewriter()`.

## Status

Rebuilt one sim at a time. **Built:** Notes. The rest show as "soon" in the
launcher and are ported from `legacy/` next.

## Deploy

Import the repo at vercel.com (Vite is auto-detected: build `vite build`, output
`dist/`). `vercel.json` sets `no-store` so OBS always gets the freshest build.
