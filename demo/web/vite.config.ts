import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const engine = fileURLToPath(new URL('../engine/target/javascript/engine.js', import.meta.url));
if (!existsSync(engine)) {
  throw new Error('engine.js is missing. Run ../engine/verify.sh first.');
}

export default defineConfig({
  // GitHub Pages serves the site from the repository's name
  base: '/doc_store_search_engine/',
  plugins: [react()],
  resolve: { alias: { '@engine': engine } },
  server: { fs: { allow: ['..'] } },
  worker: { format: 'es' },
});
