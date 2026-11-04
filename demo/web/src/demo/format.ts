import type { Response, Row } from '../engine/types';

export const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`;

/** Browsers coarsen their timers, so a fast call can't honestly be shown any more precisely than this. */
export function formatTime(nanos: number | null | undefined): string | null {
  if (nanos === null || nanos === undefined) {
    return null;
  }
  return nanos < 1_000_000 ? 'under 1 ms' : `${Math.round(nanos / 1_000_000)} ms`;
}

export function formatBytes(bytes: number): string {
  return bytes < 1000 ? `${bytes} bytes` : `${(bytes / 1000).toFixed(1)} KB`;
}

/** What a call did, in the words the API strip shows after the call itself. */
export function describeCall(response: Response): string {
  const parts: string[] = [];
  const { result, events, call } = response;
  if (Array.isArray(result)) {
    parts.push(call?.startsWith('store.deleteAll') ? `${result.length} deleted` : plural(result.length, 'result'));
  } else if (call?.startsWith('store.get')) {
    parts.push(result ? '1 document' : 'nothing stored there');
  }
  if (events?.added.length && call?.startsWith('store.undo')) {
    parts.push(`${events.added.length} restored`);
  }
  if (events?.evicted.length) {
    parts.push(`${events.evicted.length} moved to disk`);
  }
  if (events?.loaded.length) {
    parts.push(`${events.loaded.length} back in RAM`);
  }
  return parts.join(', ');
}

/** A short piece of the text around the first word that matches. */
export function snippet(row: Row, query: string, length = 120): string {
  const at = query ? row.text.search(new RegExp(`\\b${escapeRegExp(query)}`)) : -1;
  const start = at > 40 ? row.text.lastIndexOf(' ', at - 30) + 1 : 0;
  const cut = row.text.lastIndexOf(' ', start + length);
  const end = cut > start ? cut : row.text.length;
  return `${start > 0 ? '…' : ''}${row.text.slice(start, end)}${end < row.text.length ? '…' : ''}`;
}

export function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
