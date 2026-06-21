/* Parse an Email script into an ordered list of compose operations.

   The script is a single stream where plain text is typed into whichever field
   is currently focused, and [[directives]] punctuate it:

     [[from:Jordan]]    set the sender display name (avatar initials + account)
     [[account:j@x.io]] set the sender email shown in the From row (optional)
     [[to]]             focus the To field; following text types into it
     [[subject]]        focus the Subject field
     [[body]]           focus the body (this is the default field at the start
                        too, but you almost always lead with [[to]])
     [[pause:1200]]     wait 1200ms (e.g. between fields, before sending)
     [[send]]           tap Send → "Sending…" → "Sent" snackbar

   IMPORTANT: typo markup like [[exited|excited]] is NOT a directive — it stays
   in the typed text so the typewriter expands it (type the wrong word, fix it).
   Anything in [[ ]] that isn't a known directive is treated as literal typed
   text, so it survives into whichever field is focused. */

import { parseEffectDirective } from '../../recording/effects/directives';
import type { EffectOp } from '../../recording/effects/directives';

export type Field = 'to' | 'cc' | 'subject' | 'body';

export type EmailOp =
  | { kind: 'type'; field: Field; text: string }
  | { kind: 'pause'; ms: number }
  | { kind: 'send' }
  | { kind: 'fx'; op: EffectOp };

export interface Email {
  from: string;
  account: string;
  ops: EmailOp[];
}

export function parseEmail(script: string): Email {
  const ops: EmailOp[] = [];
  let from = 'You';
  let account = '';
  let field: Field = 'to';
  let buf = '';

  const flush = () => {
    if (buf.length > 0) {
      ops.push({ kind: 'type', field, text: buf });
      buf = '';
    }
  };

  const re = /\[\[([^\]]*)\]\]/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(script)) !== null) {
    buf += script.slice(last, m.index);
    last = re.lastIndex;
    const body = m[1];
    const colon = body.indexOf(':');
    const head = (colon >= 0 ? body.slice(0, colon) : body).trim();
    const arg = colon >= 0 ? body.slice(colon + 1) : '';

    switch (head) {
      case 'from':
        from = arg.trim() || from;
        break;
      case 'account':
        account = arg.trim();
        break;
      case 'to':
      case 'cc':
      case 'subject':
      case 'body':
        flush();
        field = head;
        break;
      case 'pause':
        flush();
        ops.push({ kind: 'pause', ms: clampMs(arg, 800) });
        break;
      case 'send':
        flush();
        ops.push({ kind: 'send' });
        break;
      default: {
        // a universal effect directive (notif/zoom/cursor/annotation/say/…)?
        const fx = parseEffectDirective(head, arg);
        if (fx) { flush(); ops.push({ kind: 'fx', op: fx }); break; }
        // otherwise not a directive (e.g. typo markup [[a|b]]) — literal text.
        buf += '[[' + body + ']]';
        break;
      }
    }
  }
  buf += script.slice(last);
  flush();

  if (!account) account = defaultAccount(from);
  return { from, account, ops };
}

/** A plausible gmail address from the sender name, when none is given. */
function defaultAccount(from: string): string {
  const handle = from.trim().toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.|\.$/g, '');
  return (handle || 'me') + '@gmail.com';
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
