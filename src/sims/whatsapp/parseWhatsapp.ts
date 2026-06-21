/* Parse a WhatsApp script. Plain text is what YOU type in the composer.
   Directives:

     [[name:Maya]]        contact name (top of script)
     [[send]]             commit typed text as a sent (green) bubble
     [[recv:hey!]]        a received bubble
     [[delivered]] [[read]]   ticks under the last sent bubble (read = blue)
     [[typing:1500]]      their typing indicator
     [[pause:800]]        wait
     [[react:heart]]      emoji reaction on the last bubble
     [[story:Maya|caption]]      next SENT bubble replies to Maya's status
     [[recvstory:You|caption]]   next RECEIVED bubble replies to your status
   plus universal effects ([[say:…]], [[zoom:…]], …). [[a|b]] typo markup stays. */

import { parseEffectDirective } from '../../recording/effects/directives';
import type { EffectOp } from '../../recording/effects/directives';

export interface Quote { author: string; caption: string; mine: boolean }

export type WaOp =
  | { kind: 'type'; text: string }
  | { kind: 'send' }
  | { kind: 'recv'; text: string }
  | { kind: 'delivered' }
  | { kind: 'read' }
  | { kind: 'typing'; ms: number }
  | { kind: 'pause'; ms: number }
  | { kind: 'react'; emoji: string }
  | { kind: 'story'; quote: Quote; side: 'sent' | 'recv' }
  | { kind: 'fx'; op: EffectOp };

export interface WaChat { name: string; ops: WaOp[] }

const REACTIONS: Record<string, string> = {
  heart: '❤️', like: '👍', thumbsup: '👍', laugh: '😂', haha: '😂',
  wow: '😮', sad: '😢', cry: '😢', pray: '🙏', fire: '🔥',
};

export function parseWhatsapp(script: string): WaChat {
  const ops: WaOp[] = [];
  let name = 'Maya';
  let buf = '';
  const flush = () => { if (buf.length) { ops.push({ kind: 'type', text: buf }); buf = ''; } };

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
      case 'name': name = arg.trim() || name; break;
      case 'send': flush(); ops.push({ kind: 'send' }); break;
      case 'recv': flush(); ops.push({ kind: 'recv', text: arg }); break;
      case 'delivered': flush(); ops.push({ kind: 'delivered' }); break;
      case 'read': flush(); ops.push({ kind: 'read' }); break;
      case 'typing': flush(); ops.push({ kind: 'typing', ms: clampMs(arg, 1500) }); break;
      case 'pause': flush(); ops.push({ kind: 'pause', ms: clampMs(arg, 800) }); break;
      case 'react': flush(); ops.push({ kind: 'react', emoji: REACTIONS[arg.trim().toLowerCase()] ?? '❤️' }); break;
      case 'story': {
        flush();
        const [author, caption] = arg.split('|');
        ops.push({ kind: 'story', side: 'sent', quote: { author: (author || name).trim(), caption: (caption || '').trim(), mine: false } });
        break;
      }
      case 'recvstory': {
        flush();
        const [author, caption] = arg.split('|');
        ops.push({ kind: 'story', side: 'recv', quote: { author: (author || 'You').trim(), caption: (caption || '').trim(), mine: true } });
        break;
      }
      default: {
        const fx = parseEffectDirective(head, arg);
        if (fx) { flush(); ops.push({ kind: 'fx', op: fx }); break; }
        buf += '[[' + body + ']]';
        break;
      }
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
