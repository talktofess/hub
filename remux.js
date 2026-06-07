/* remux.js — fix OBS keystroke-audio drift by replacing it with the clean,
   deterministically-rendered keystroke track and locking it to the exact frame.

   Workflow:
     1. Record the take in OBS with the browser source pointed at the #render
        pass (build-obs-scenes.js already does this). The keystroke audio is
        muted in that recording; a tiny white sync-marker flashes in the
        top-left corner at the first keystroke.
     2. Render the clean keystroke track: open
            hub.html#audiocap&sim=<NAME>
        in a normal browser (same hub, so it has the same script -> same seed
        -> identical take), click once, let it type to the end. It downloads
            <sim>__t0-<ms>ms.webm
        where <ms> is how far into that file the first keystroke lands.
     3. Mux:
            node remux.js <obs-video> <sim>__t0-<ms>ms.webm [out.mp4]

   What this does:
     - finds the first frame where the corner marker lights up  -> t0_video
     - trims the clean track to its first keystroke (from the t0 in its name)
     - delays it to t0_video so the first keystroke lands on the exact frame
     - mixes it over any background audio OBS captured, masks the marker, and
       writes a new MP4. No accumulating drift: both halves are one seeded take.

   Requires ffmpeg (and ffprobe) on PATH.                                       */
'use strict';
const { execFileSync, spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

function fail(msg, code) { console.error('remux: ' + msg); process.exit(code || 1); }

// args: positional <video> <audio> [out], plus optional --offset=<ms>
// (negative pulls the keystroke audio EARLIER, i.e. fixes audio that lags).
let offsetMs = 0;
const pos = [];
for (const a of process.argv.slice(2)) {
  const mo = /^--offset=?(-?\d+(?:\.\d+)?)$/.exec(a);
  if (mo) { offsetMs = parseFloat(mo[1]); continue; }
  pos.push(a);
}
const [videoArg, audioArg, outArg] = pos;
if (!videoArg || !audioArg) {
  fail('usage: node remux.js <obs-video> <sim__t0-<ms>ms.webm> [out.mp4] [--offset=<ms>]', 64);
}
const video = path.resolve(videoArg);
const audio = path.resolve(audioArg);
if (!fs.existsSync(video)) fail('video not found: ' + video, 66);
if (!fs.existsSync(audio)) fail('audio not found: ' + audio, 66);
const out = path.resolve(outArg || video.replace(/\.[^.\\/]+$/, '') + '.synced.mp4');

// how far into the clean track the first keystroke sits. The filename value
// comes from performance.now() and includes MediaRecorder startup latency, so
// it reads a bit late and shows up as a CONSTANT lag — we measure the real
// first-sound onset below and prefer that, falling back to the filename.
const tm = /__t0-(\d+)ms/.exec(path.basename(audio));
const audioT0FromName = tm ? (+tm[1]) / 1000 : 0;

function ffmpeg(args, opts) {
  const r = spawnSync('ffmpeg', args, Object.assign({ encoding: 'utf8' }, opts || {}));
  if (r.error) fail('could not run ffmpeg (is it on PATH?): ' + r.error.message, 69);
  return r;
}

// ---- 1. locate the sync-marker -> t0_video --------------------------------
// Sample mean luminance of a small top-left crop per frame; the first frame
// above a bright threshold is the marker (white square on the dark backdrop).
console.error('remux: scanning for the sync-marker…');
// A Windows abs path (C:/…) can't go inside a filtergraph file= option — its
// colon is the option separator. So write to a bare filename and run ffmpeg
// from the temp dir, sidestepping escaping entirely.
const tmpDir = os.tmpdir();
const markName = 'simmark_' + process.pid + '.txt';
const tmp = path.join(tmpDir, markName);
ffmpeg(['-hide_banner', '-i', video,
  '-vf', 'crop=28:28:0:0,signalstats,metadata=print:file=' + markName,
  '-an', '-f', 'null', '-'], { cwd: tmpDir, stdio: ['ignore', 'ignore', 'inherit'] });

if (!fs.existsSync(tmp)) fail('marker scan produced no data (ffmpeg/ffprobe ok?).', 70);
const meta = fs.readFileSync(tmp, 'utf8');
try { fs.unlinkSync(tmp); } catch (e) {}

let videoT0 = null, curT = null;
const BRIGHT = 90;                       // 0..255; white marker ≫ this, dark backdrop ≪
for (const line of meta.split(/\r?\n/)) {
  const mt = /pts_time:([0-9.]+)/.exec(line);
  if (mt) { curT = parseFloat(mt[1]); continue; }
  const my = /signalstats\.YAVG=([0-9.]+)/.exec(line);
  if (my && curT != null && parseFloat(my[1]) > BRIGHT) { videoT0 = curT; break; }
}
if (videoT0 == null) {
  fail('no sync-marker found in the top-left corner.\n' +
    '       Was the video recorded with the #render pass (not #present)?', 71);
}
// measure the true first-sound onset in the clean track (end of the leading silence)
let audioT0 = audioT0FromName;
const sd = spawnSync('ffmpeg', ['-hide_banner', '-i', audio,
  '-af', 'silencedetect=noise=-45dB:d=0.03', '-f', 'null', '-'], { encoding: 'utf8' });
const sm = /silence_end:\s*([0-9.]+)/.exec(sd.stderr || '');
if (sm) {
  audioT0 = parseFloat(sm[1]);
  console.error('remux: measured first-sound onset at ' + audioT0.toFixed(3) + 's (filename said ' + audioT0FromName.toFixed(3) + 's).');
} else {
  console.error('remux: could not measure onset; using filename t0 ' + audioT0FromName.toFixed(3) + 's.');
}
console.error('remux: marker at ' + videoT0.toFixed(3) + 's; first keystroke at ' + audioT0.toFixed(3) + 's.');

// ---- 2. does the OBS take carry any (background) audio to keep? ------------
let hasBgAudio = false;
const pr = spawnSync('ffprobe', ['-v', 'error', '-select_streams', 'a',
  '-show_entries', 'stream=index', '-of', 'csv=p=0', video], { encoding: 'utf8' });
if (!pr.error && pr.stdout && pr.stdout.trim()) hasBgAudio = true;

// ---- 3. build the synced MP4 ----------------------------------------------
// place the clean track's first keystroke at the marker frame, shifted by --offset
const placeSec = videoT0 + offsetMs / 1000;
let fc;
if (placeSec >= 0) {
  // trim the clean track to its first keystroke, then delay it onto the target time
  fc = '[1:a]atrim=start=' + audioT0.toFixed(4) +
    ',asetpts=PTS-STARTPTS,adelay=' + Math.round(placeSec * 1000) + ':all=1[keys];';
} else {
  // negative target: drop |placeSec| from the front of the clean track instead of delaying
  fc = '[1:a]atrim=start=' + (audioT0 - placeSec).toFixed(4) +
    ',asetpts=PTS-STARTPTS[keys];';
}
if (offsetMs) console.error('remux: applying --offset ' + offsetMs + 'ms (first keystroke at ' + placeSec.toFixed(3) + 's).');
if (hasBgAudio) {
  fc += '[0:a][keys]amix=inputs=2:normalize=0:duration=first[aout];';
} else {
  fc += '[keys]anull[aout];';
}
// mask the marker corner out of the final picture (drawbox forces a re-encode)
fc += '[0:v]drawbox=x=0:y=0:w=ih*0.06:h=ih*0.06:color=black:t=fill[vout]';

const args = ['-hide_banner', '-y', '-i', video, '-i', audio,
  '-filter_complex', fc,
  '-map', '[vout]', '-map', '[aout]',
  '-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
  '-c:a', 'aac', '-b:a', '192k',
  '-shortest', '-movflags', '+faststart', out];

console.error('remux: writing ' + out + (hasBgAudio ? '  (clean keystrokes mixed over OBS background audio)' : '  (clean keystrokes)') + '…');
const enc = ffmpeg(args, { stdio: ['ignore', 'ignore', 'inherit'] });
if (enc.status !== 0) fail('ffmpeg encode failed (exit ' + enc.status + ').', enc.status || 1);
console.error('remux: done -> ' + out);
