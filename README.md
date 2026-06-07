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
npm run dev        # http://localhost:3000  (edit mode)
npm run build      # -> dist/  (what Vercel serves)
npm run typecheck
```

## How it works

- **Edit mode** (`/`): a launcher (left), the live device stage (center), and a
  control panel (right) — script editor, ▶ Preview, background media, and
  "Copy OBS URL" buttons.
- The stage renders each sim at a fixed **logical size** (1080×1920) and scales
  it to fit, so it looks identical at any OBS source resolution.
- A script is typed character-by-character with **keystroke audio**. Body text
  supports the typo-and-correct markup `[[wrong|right]]` — types the wrong
  spelling, backspaces, types the correction.

### Recording (OBS)

The recording pipeline from the legacy tool is preserved — modes live in the URL
hash so they work as a Browser Source with no server routing:

| URL hash                       | Pass | Behavior |
|--------------------------------|------|----------|
| `#present&sim=NAME&s=…`         | audible preview take | autostarts one take, audible keystrokes |
| `#render&sim=NAME&s=…`          | OBS **video** pass | seeded (reproducible take), flashes the corner **sync marker**, **mutes** keystrokes |
| `#audiocap&sim=NAME&s=…`        | clean **audio** pass | same seed ⇒ identical take; records keystrokes to `NAME__t0-<ms>ms.webm` |

Use the **Video** + **Audio** buttons in the control panel to copy these URLs.
Optional `&bg=`, `&bgloop=`, `&bgsrt=` carry background media + SRT pacing.
Mux the clean audio onto the OBS video with `legacy/remux.js` (ffmpeg) — see
`legacy/RENDER.md`. Because it's served over **https**, the audio pass
(`MediaRecorder`) and clipboard work reliably (both need a secure context).

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
