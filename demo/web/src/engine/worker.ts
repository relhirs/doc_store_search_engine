// The engine runs here so that a disk-heavy call can't hold up an animation on the page.
import * as engine from '@engine';

const exportsByName = engine as unknown as Record<string, (...args: unknown[]) => string>;

self.onmessage = ({ data: { id, op, args } }: MessageEvent) => {
  try {
    self.postMessage({ id, json: exportsByName[op](...args) });
  } catch {
    // Every call gets an answer, or the page would wait for it forever
    const { state } = JSON.parse(exportsByName.snapshot());
    self.postMessage({ id, json: JSON.stringify({ ok: false, call: null, error: 'The engine failed on that call', state }) });
  }
};

self.postMessage({ ready: true });
