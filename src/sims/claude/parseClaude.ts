/* Parse a Claude CLI script.

   Everything before [[reply]] is the prompt the user types; everything after is
   Claude's streamed answer. Header directives (before [[reply]]) configure the
   welcome screen and the thinking beat:

     [[model:Claude Opus 4.8]]   model name shown in the welcome box
     [[name:Dev]]                "Welcome back <name>!"
     [[account:you@host]]        org / account line
     [[cwd:C:\\Users\\you]]       working directory
     [[version:2.1.180]]         Claude Code version
     [[think:2800]]              thinking duration (ms)
     [[verb:Pondering]]          the thinking verb (else a random one)

   In the prompt body, [[wrong|right]] types a typo and fixes it, and
   [[pause:600]] lifts the hands. */

export interface ClaudeScript {
  model: string;
  name: string;
  account: string;
  cwd: string;
  version: string;
  thinkMs: number;
  verb: string | null;
  prompt: string; // may contain [[a|b]] and [[pause:ms]]
  reply: string;
}

const DEFAULTS = {
  model: 'Claude Opus 4.8',
  name: 'Dev',
  account: 'claude.dev@gmx.ch',
  cwd: 'C:\\Users\\atjul',
  version: '2.1.180',
  thinkMs: 2800,
};

export function parseClaude(script: string): ClaudeScript {
  let { model, name, account, cwd, version, thinkMs } = DEFAULTS;
  let verb: string | null = null;
  const promptLines: string[] = [];
  const replyLines: string[] = [];
  let inReply = false;

  for (const ln of script.split('\n')) {
    if (/^\s*\[\[\s*reply\s*\]\]\s*$/i.test(ln)) { inReply = true; continue; }
    const hd = ln.match(/^\s*\[\[\s*(model|name|account|cwd|version|think|verb):([\s\S]*?)\]\]\s*$/i);
    if (hd && !inReply) {
      const k = hd[1].toLowerCase();
      const v = hd[2].trim();
      if (k === 'model') model = v || model;
      else if (k === 'name') name = v || name;
      else if (k === 'account') account = v || account;
      else if (k === 'cwd') cwd = v || cwd;
      else if (k === 'version') version = (v || version).replace(/^v/i, '');
      else if (k === 'think') thinkMs = clampMs(v, thinkMs);
      else if (k === 'verb') verb = v || null;
      continue;
    }
    (inReply ? replyLines : promptLines).push(ln);
  }

  return {
    model, name, account, cwd, version, thinkMs, verb,
    prompt: promptLines.join('\n').replace(/^\n+|\n+$/g, ''),
    reply: replyLines.join('\n').replace(/^\n+|\n+$/g, ''),
  };
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 60000));
}
