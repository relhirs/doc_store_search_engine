export async function measure() {
  const t0 = performance.now();
  const m = await import('./engine.js');
  const t1 = performance.now();
  m.reset();
  for (let i = 0; i < 24; i++) m.putText(`http://x/d${i}`, `tree trie doc${i} sample text for a first paint`);
  m.setMaxDocs(16);
  const hits = m.searchPrefix('tr').split(' ').length;
  const t2 = performance.now();
  const trace = m.runScript();
  const t3 = performance.now();
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(trace));
  const sha = [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 16);
  return { importMs: +(t1 - t0).toFixed(1), loadAndSearchMs: +(t2 - t1).toFixed(1), scriptMs: +(t3 - t2).toFixed(1), hits, sha, chars: trace.length };
}
