# Sim Hub

Typed-animation recreations of everyday apps (Notes, iMessage, Email, …) for
screen-recording short videos.

**Now being rebuilt as a native Android app** (Kotlin + Jetpack Compose, in
[`app/`](./app)) so it runs on a phone and **records itself to the gallery** —
no desktop, no OBS. The original **Vite + React** web app (`src/`) is kept as the
**reference spec** for what each sim looks like and what "completely editable"
means; the native app ports it sim-by-sim. See
[`memory/`](./memory) for the rewrite decisions.

## Android app (the rebuild)

```bash
./gradlew :app:assembleDebug      # build the APK
./gradlew :app:installDebug       # build + install on a connected device
```

Status — **Phase 1 (native hub)**: core engine ported (typing engine, 9-profile
keystroke-audio synth, settings), first sim live (**Notes**), scaling stage +
script editor. Remaining: the full settings drawer, the other 10 sims, then
**Phase 2** — on-device record-to-gallery (capture the present screen + app
audio, save via MediaStore).

The rest of this README documents the **React reference** app.

---

This was a ground-up rebuild for a more app-like feel. The previous
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

### Settings drawer — universal tabs + per-sim tabs

The drawer is **tabbed**. The left set of tabs is universal (applies to every
sim, baked into the one URL); the active sim then contributes **its own tabs**
(e.g. Email adds Layout / Gmail / Compose / Inbox / Camera / Notif).

Universal tabs:

- **Sound** — 9 keystroke profiles (mechanical, blue switch, typewriter,
  vintage, tactile, soft, marshmallow, bubble, **pencil**) + volume, Test. A sim
  can override this for its own feel (Journal → pencil, Typewriter → typewriter).
- **Timing** — speed, hesitation, timing jitter, auto-mistakes (fumble +
  self-correct), start delay, loop + hold
- **Caret** — show/blink/color/style (bar / block / underline)
- **Look** — theme, accent, text scale, grain, vignette
- **Media** — uploaded background media (see below)
- **Narrate** — on-screen **subtitles**, spoken **voiceover** (browser TTS:
  voice, rate, pitch) for `[[say:…]]` lines, and an optional **SRT** caption
  track synced to the take clock

### Universal effect directives (any sim's script)

Reusable across sims, rendered as overlays on the stage and recorded by OBS:

- `[[notif:GitHub|CI passed|all checks green]]` — slide-in notification (stacks,
  auto-dismisses, optional chime)
- `[[zoom:body]]` / `[[zoom:0.5,0.3,1.8]]` / `[[zoomout]]` — camera punch-in; the
  zoomed-in look **persists** until released
- `[[cursor:send]]` / `[[click]]` — a fake cursor that moves and clicks
- `[[lens:0.5,0.3]]` / `[[lens:off]]` — spotlight that dims everything else
- `[[arrow:0.2,0.3>0.6,0.7|look]]`, `[[string:…|note]]`, `[[box:x,y,w,h|here]]` —
  on-screen annotations (coords are 0..1 of the frame)
- `[[say:Narration line]]` — subtitle + optional TTS · `[[clearfx]]` — clear them

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
  recording/         deterministic RNG, URL/mode parsing, keystroke audio engine,
                     typing engine, provider/hook, per-sim settings, sync marker
  recording/effects/ reusable overlays: notifications, camera (persistent zoom),
                     fake cursor, spotlight, annotations, subtitles, TTS speech
  sims/              one folder per sim + a registry; types.ts is the sim contract
  shell/             launcher, stage (resolution-independent scaling), tabbed
                     settings drawer, control panel
```

Adding a sim = a folder under `src/sims/` exporting a `SimDef` (id, label, glyph,
logical size, default script, component). Optionally it declares
`defaultSettings`, `settingsTabs` (its own drawer tabs, read/written with
`useSimSettings()`), and `getLogical()` to pick its render size from its own
settings (e.g. Email flips between 1920×1080 desktop and 1080×1920 reel).
Register it in `src/sims/registry.ts`. The component drives the shared typing
engine via `useTypewriter()` and can fire overlays via `useEffects()`.

## Sims

Notes · iMessage · **WhatsApp** (chat + story replies) · **Email** (switchable
desktop Gmail ⇄ mobile reel, editable inbox, notifications, auto-zoom camera) ·
Lists · Corporate · Typer · **Typewriter** (mechanical strikes + carriage-return
bell & swipe) · TikTok · Claude · Journal (graphite pencil).

## Deploy

Import the repo at vercel.com (Vite is auto-detected: build `vite build`, output
`dist/`). `vercel.json` sets `no-store` so OBS always gets the freshest build.
