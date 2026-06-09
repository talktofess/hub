/* Parse an iMessage script into an ordered list of chat operations.

   The script is a single stream where plain text is what *you* type into the
   composer, and [[directives]] punctuate it:

     [[name:Alex]]      set the contact name (optional; first thing in the script)
     [[send]]           commit the currently-typed text as a sent (blue) bubble
     [[delivered]]      show "Delivered" under the last sent bubble
     [[read]]           show "Read" under the last sent bubble
     [[pause:1200]]     wait 1200ms
     [[typing:1600]]    show their typing indicator for 1600ms
     [[recv:hey]]       a received (grey) bubble with this text
     [[react:heart]]    a tapback on the last bubble (heart|haha|like|dislike|exclaim|question)

   IMPORTANT: typo markup like [[witing|writing]] is NOT a directive — it stays
   in the typed text so the typewriter expands it (type the wrong word, fix it).
   Anything in [[ ]] that isn't a known directive is treated as literal typed text. */

export type ChatOp =
  | { kind: 'type'; text: string }
  | { kind: 'send' }
  | { kind: 'delivered' }
  | { kind: 'read' }
  | { kind: 'pause'; ms: number }
  | { kind: 'typing'; ms: number }
  | { kind: 'recv'; text: string }
  | { kind: 'react'; emoji: string };

export interface Chat {
  name: string;
  ops: ChatOp[];
}

const REACTIONS: Record<string, string> = {
  heart: '❤️',
  like: '👍',
  thumbsup: '👍',
  dislike: '👎',
  thumbsdown: '👎',
  haha: '😂',
  laugh: '😂',
  exclaim: '‼️',
  emphasize: '‼️',
  question: '❓',
};

export function parseChat(script: string): Chat {
  const ops: ChatOp[] = [];
  let name = 'Alex';
  let buf = '';

  const flush = () => {
    if (buf.length > 0) {
      ops.push({ kind: 'type', text: buf });
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
      case 'name':
        name = arg.trim() || name;
        break;
      case 'send':
        flush();
        ops.push({ kind: 'send' });
        break;
      case 'delivered':
        flush();
        ops.push({ kind: 'delivered' });
        break;
      case 'read':
        flush();
        ops.push({ kind: 'read' });
        break;
      case 'pause':
        flush();
        ops.push({ kind: 'pause', ms: clampMs(arg, 800) });
        break;
      case 'typing':
        flush();
        ops.push({ kind: 'typing', ms: clampMs(arg, 1500) });
        break;
      case 'recv':
        flush();
        ops.push({ kind: 'recv', text: arg });
        break;
      case 'react':
        flush();
        ops.push({ kind: 'react', emoji: REACTIONS[arg.trim().toLowerCase()] ?? '❤️' });
        break;
      default:
        // Not a directive (e.g. typo markup [[a|b]]) — keep it as literal text.
        buf += '[[' + body + ']]';
        break;
    }
  }
  buf += script.slice(last);
  flush();

  return { name, ops };
}

function clampMs(s: string, dflt: number): number {
  const n = parseInt(s.trim(), 10);
  if (Number.isNaN(n)) return dflt;
  return Math.max(0, Math.min(n, 20000));
}
