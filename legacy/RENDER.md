# Deterministic render — fixing OBS keystroke-audio drift

> **Hosted on Vercel?** The hub now runs as a static app — see `DEPLOY.md`.
> Everywhere this doc shows `file:///A:/…/hub.html`, use your deployment URL
> instead, e.g. `https://your-app.vercel.app/`. The flow is otherwise identical
> (and the audio pass works *better* over https — it's a secure context).


OBS captures the browser's video almost instantly but its **audio** through a
buffered loopback, so the keystroke sounds drift behind the typing (and the lag
wanders take to take). This pipeline stops relying on OBS for the keystroke
audio: the typing sound is rendered **deterministically** from the same seeded
take and muxed back onto the OBS video, locked to the exact frame. No realtime
audio capture = no drift.

## How it works

Every sim is bundled into `hub.html` (`node build-hub.js`) and loaded in an
iframe. The hub injects one shared shim (`__simRenderShim__` in `build-hub.js`)
into that iframe for two render passes:

| Hash on `hub.html`        | Pass            | What the shim does |
|---------------------------|-----------------|--------------------|
| `#render&sim=NAME`        | **OBS video**   | Seeds `Math.random` (reproducible take), flashes a tiny white sync-marker in the top-left corner at the first keystroke, and **mutes** the keystroke audio. OBS still records background audio. |
| `#audiocap&sim=NAME`      | **Clean audio** | Same script ⇒ **same seed ⇒ identical take.** Tees the Web-Audio master output into a `MediaRecorder` and downloads `NAME__t0-<ms>ms.webm` (`<ms>` = where the first keystroke lands in the file). |
| `#present&sim=NAME`       | preview (legacy)| Untouched — audible, drifty. For quick checks only. |

The OBS scene collection (`node build-obs-scenes.js`) already points its browser
sources at `#render`. **Re-import `Sim Hub.json` into OBS** after regenerating it.

## Recording a synced take

1. **Video** — record the take in OBS as usual (sources are on `#render`). The
   keystrokes are silent in this recording; a marker blips in the corner. Note
   the output file, e.g. `journal.mkv`.

2. **Audio** — in a normal browser, open the **same hub** (so it has the same
   stored script ⇒ same seed) at:

   ```
   file:///A:/work/exhibit%20A/1s%20and%200s/1v0/hub.html#audiocap&sim=journal
   ```

   Click once, let it type one full take, then click **■ Stop & save**
   (non-looping sims stop on their own). A `journal__t0-<ms>ms.webm` downloads.

   > Render audio from the same hub session where you recorded, or use the
   > **📋 OBS URL** button to bake the script into the URL (`&s=…`) so a fresh
   > browser reproduces the identical take.

3. **Mux:**

   ```
   node remux.js journal.mkv journal__t0-1234ms.webm
   ```

   Writes `journal.synced.mp4`: the clean keystroke track aligned to the marker
   frame, **mixed over any background audio** OBS captured, with the marker
   masked out. Requires `ffmpeg`/`ffprobe` on PATH.

## Why it stays in sync

- Both passes run the **same seeded take**, so timing and per-keystroke sound
  params are identical (`Math.random` → seeded mulberry32, reseeded at play
  start). Verified: same script ⇒ identical RNG stream; different script ⇒
  different take.
- Alignment is a **single offset** at the first keystroke (the marker), not a
  running sync between two clocks — so nothing accumulates. Looping takes stay
  aligned because the loop is deterministic from the same seed.

## If the audio is still uniformly early/late

`remux.js` measures the **real first-sound onset** in the clean track (via
`silencedetect`) rather than trusting the filename's `t0`, which removes the
constant offset that `MediaRecorder` startup latency would otherwise add. Any
small residual (e.g. the corner marker rendering ~1 frame late in OBS) is a
fixed amount — dial it out:

```
node remux.js <video> <audio.webm> --offset=-80
```

Negative pulls the keystrokes **earlier** (fixes audio that lags); positive
pushes them later. This re-runs on your existing files — no re-record needed.

> Note: a single `--offset` only works if the lag is **constant** across the
> clip. If it instead *grows* toward the end, the two render passes drifted
> apart in real time (CPU load) and the fix is to capture audio in the same run
> OBS records — ask and I'll wire that single-run mode.

## Notes / limits

- Marker detection is frame-quantized (≤ ~1 frame ≈ 33 ms at 30 fps), erring
  toward audio slightly late (the tolerable direction).
- `remux.js` re-encodes video (needed to mask the marker): libx264 CRF 18.
- If you record with `#present` instead of `#render`, `remux.js` will report
  "no sync-marker found" — that recording has no marker and drifty audio.
