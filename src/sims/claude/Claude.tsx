import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRecording } from '../../recording/useRecording';
import { charDelay, settledText, sleep, tokenize } from '../../recording/typer';
import { parseClaude } from './parseClaude';
import './claude.css';

const SPIN = ['✻', '✶', '✳', '✺', '✸', '✷'];
const VERBS = ['Thinking', 'Pondering', 'Percolating', 'Noodling', 'Conjuring', 'Cogitating', 'Ruminating', 'Finessing'];
const TRACE = ['parsing the request', 'weighing a couple angles', 'drafting a first pass', 'checking the tone', 'trimming the fat', 'almost there'];

interface Think { glyph: string; verb: string; secs: number; tokens: string; trace: string }

/* A faithful Claude Code CLI: a PowerShell prompt types `claude`, the welcome
   box fades in, the user prompt is typed into the conversation, a thinking
   spinner ticks with rotating verbs + token stats, then the reply streams in.
   Runs on a bespoke loop (not the shared typewriter) because the flow — launch,
   think, stream — is its own thing, but it still honours the universal speed /
   start-delay / keystroke-audio / loop settings. */
export function Claude() {
  const rec = useRecording();
  const data = useMemo(() => parseClaude(rec.script), [rec.script]);
  const preview = !rec.playing && rec.mode === 'edit';

  const [psCmd, setPsCmd] = useState('');
  const [welcome, setWelcome] = useState(false);
  const [userText, setUserText] = useState('');
  const [userTyping, setUserTyping] = useState(false);
  const [think, setThink] = useState<Think | null>(null);
  const [reply, setReply] = useState('');

  const tokenRef = useRef(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [psCmd, welcome, userText, think, reply]);

  const reset = useCallback(() => {
    setPsCmd(''); setWelcome(false); setUserText(''); setUserTyping(false);
    setThink(null); setReply('');
  }, []);

  const stop = useCallback(() => { tokenRef.current++; rec.setPlaying(false); }, [rec]);

  const run = useCallback(async () => {
    const my = ++tokenRef.current;
    const aborted = () => tokenRef.current !== my;
    const st = rec.settings;
    const speed = st.speed || 1;
    let firstKey = false;
    const key = () => {
      if (!firstKey) { firstKey = true; if (rec.mode === 'render') rec.flashMarker(); }
      rec.audio.key();
    };

    // type a string (with [[a|b]] typos + [[pause:ms]]) into a setter
    const typeInto = async (full: string, set: (s: string) => void): Promise<boolean> => {
      let buf = '';
      for (const chunk of splitPauses(full)) {
        for (const a of tokenize(chunk.text)) {
          if (aborted()) return false;
          if (a.t === 'wait') { await sleep(a.ms / speed); continue; }
          if (a.t === 'back') { buf = buf.slice(0, -1); set(buf); await sleep((45 + Math.random() * 35) / speed); continue; }
          buf += a.ch; set(buf); key(); await sleep(charDelay(a.ch, st) / speed);
        }
        if (chunk.pause) { await sleep(chunk.pause); }
      }
      return true;
    };

    rec.setPlaying(true);
    await rec.audio.resume();

    do {
      firstKey = false;
      reset();
      await sleep(st.startDelay / speed);
      if (aborted()) return;

      // 1) launch — type `claude` at the PowerShell prompt
      if (!(await typeInto('claude', setPsCmd))) return;
      await sleep(320 / speed);
      key();

      // 2) welcome screen fades in
      setWelcome(true);
      await sleep(760 / speed);
      if (aborted()) return;

      // 3) type the prompt into the conversation
      setUserTyping(true);
      if (!(await typeInto(data.prompt, setUserText))) return;
      await sleep(420 / speed);
      key();
      setUserTyping(false);
      await sleep(360 / speed);
      if (aborted()) return;

      // 4) thinking beat
      if (!(await runThink(data.verb, data.thinkMs, setThink, aborted))) return;
      setThink(null);
      await sleep(160);

      // 5) stream the reply
      if (!(await streamReply(data.reply, speed, setReply, key, aborted))) return;

      if (st.loop && !rec.oneShot && !aborted()) await sleep(st.holdEnd);
    } while (rec.settings.loop && !rec.oneShot && !aborted());

    if (aborted()) return;
    rec.setPlaying(false);
    if (rec.capturing) setTimeout(() => rec.audio.stopCapture(), 700);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec, data, reset]);

  useEffect(() => {
    if (rec.playSignal > 0) run();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.playSignal]);
  useEffect(() => {
    if (rec.stopSignal > 0) stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rec.stopSignal]);

  // --- static preview (edit mode, not playing) ---
  const sPsCmd = preview ? 'claude' : psCmd;
  const sWelcome = preview ? true : welcome;
  const sUser = preview ? settledText(data.prompt) : userText;
  const sReply = preview ? data.reply : reply;
  const showUser = preview || welcome;
  const showUserCaret = !preview && rec.playing && userTyping;

  return (
    <div className="cl-root">
      <div className="cl-scroll" ref={scrollRef}>
        <div className="cl-psline">
          <span className="cl-path">PS {data.cwd}&gt; </span>
          <span className="cl-cmd">{sPsCmd}</span>
          {!sWelcome && <span className="caret" />}
        </div>

        {sWelcome && <Welcome data={data} />}

        {showUser && (sUser || showUserCaret) && (
          <div className="cl-user">
            <span className="cl-chev">&gt;</span>
            <span className="cl-utext">{sUser}{showUserCaret && <span className="caret" />}</span>
          </div>
        )}

        {think && (
          <div className="cl-think">
            <div className="cl-think-row">
              <span className="cl-spin">{think.glyph}</span>
              <span className="cl-verb">{think.verb}…</span>
              <span className="cl-stats">({think.secs}s · ↑ {think.tokens} tokens · esc to interrupt)</span>
            </div>
            {think.trace && <div className="cl-trace">{think.trace}…</div>}
          </div>
        )}

        {(sReply || (preview && data.reply)) && (
          <div className="cl-resp">
            <span className="cl-bullet">⏺</span>
            <span className="cl-rtext">{sReply}</span>
          </div>
        )}
      </div>

      {!sWelcome && !preview && (
        <div className="cl-footer">? for shortcuts&nbsp; · &nbsp;launching…</div>
      )}
    </div>
  );
}

function Welcome({ data }: { data: ReturnType<typeof parseClaude> }) {
  const short = data.model.replace(/^claude\s+/i, '');
  return (
    <div className="cl-welcome">
      <span className="cl-wb-legend">Claude Code <span className="cl-ver">v{data.version}</span></span>
      <div className="cl-mascot">✳</div>
      <div className="cl-wb-welcome">Welcome back {data.name}!</div>
      <div className="cl-wb-account">
        {short} (1M context) · Claude Max<br />
        <span className="cl-em">{data.account}</span>'s Organization<br />
        {data.cwd}
      </div>
      <div className="cl-wb-divider" />
      <div className="cl-wb-h">Tips for getting started</div>
      <div className="cl-wb-p">Run <span className="cl-cmdname">/init</span> to create a CLAUDE.md file with instructions for Claude.</div>
      <div className="cl-wb-h">What's new</div>
      <div className="cl-wb-li">• <span className="cl-cmdname">/usage</span> now shows a per-category breakdown of spend</div>
      <div className="cl-wb-li">• <span className="cl-cmdname">/diff</span> detail view can be scrolled with the keyboard</div>
      <div className="cl-wb-note">/release-notes for more</div>
    </div>
  );
}

// ---- thinking beat -------------------------------------------------------
async function runThink(
  verbIn: string | null, ms: number, set: (t: Think) => void, aborted: () => boolean,
): Promise<boolean> {
  const verb = verbIn || VERBS[Math.floor(Math.random() * VERBS.length)];
  const tokTarget = 640 + Math.floor(Math.random() * 1760);
  const t0 = performance.now();
  let si = 0;
  while (true) {
    if (aborted()) return false;
    const elapsed = performance.now() - t0;
    if (elapsed >= ms) break;
    si = (si + 1) % SPIN.length;
    const frac = Math.min(1, elapsed / ms);
    const tokens = Math.round(tokTarget * (frac * (2 - frac)));
    const traceIdx = Math.min(TRACE.length - 1, Math.floor(frac * TRACE.length));
    set({ glyph: SPIN[si], verb, secs: Math.floor(elapsed / 1000), tokens: fmtTok(tokens), trace: TRACE[traceIdx] });
    await sleep(90);
  }
  return true;
}

// ---- reply stream --------------------------------------------------------
async function streamReply(
  text: string, speed: number, set: (s: string) => void, key: () => void, aborted: () => boolean,
): Promise<boolean> {
  const toks = text.match(/\s+|[^\s]+/g) || [];
  let out = '';
  const base = 42;
  for (const t of toks) {
    if (aborted()) return false;
    out += t; set(out);
    if (/\S/.test(t)) key();
    await sleep((base + Math.random() * base * 0.8) / speed);
  }
  return true;
}

function fmtTok(n: number): string { return n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n); }

function splitPauses(s: string): { text: string; pause: number }[] {
  const out: { text: string; pause: number }[] = [];
  const re = /\[\[\s*pause:(\d+)\s*\]\]/gi;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(s)) !== null) {
    out.push({ text: s.slice(last, m.index), pause: Math.max(0, Math.min(parseInt(m[1], 10) || 0, 20000)) });
    last = re.lastIndex;
  }
  out.push({ text: s.slice(last), pause: 0 });
  return out;
}
