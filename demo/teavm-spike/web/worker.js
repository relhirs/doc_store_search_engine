import { measure } from './core.js';
measure().then(r => postMessage(r), e => postMessage({ error: String(e) }));
