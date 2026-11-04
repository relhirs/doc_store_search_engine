// Records the tour for the README and the failed-engine page. Start `npm run preview` first, then `npm run record`. Needs ffmpeg.
import { execFileSync } from 'node:child_process';
import { globSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const SITE = process.env.SITE ?? 'http://localhost:4173/doc_store_search_engine/';
const OUTPUT = fileURLToPath(new URL('../public/', import.meta.url));
const size = { width: 1280, height: 800 };
const executablePath = process.env.CHROME_PATH ?? globSync(
  `${homedir()}/Library/Caches/ms-playwright/chromium-*/*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing`,
)[0];

const dir = mkdtempSync(join(tmpdir(), 'walkthrough-'));
const browser = await chromium.launch({ executablePath, headless: true });
const page = await browser.newPage({ viewport: size });

// Headless Chrome draws no pointer, so the recording adds its own
await page.addInitScript(() => addEventListener('DOMContentLoaded', () => {
  const pointer = document.createElement('div');
  pointer.style.cssText = 'position:fixed;z-index:9999;left:-20px;top:0;width:16px;height:16px;margin:-8px 0 0 -8px;border:2px solid #16202B;border-radius:50%;background:#fff8;pointer-events:none';
  document.body.append(pointer);
  addEventListener('mousemove', event => { pointer.style.left = `${event.clientX}px`; pointer.style.top = `${event.clientY}px`; }, true);
}));

const frames = [];
await page.screencast.start({ size, quality: 90, onFrame: ({ data }) => frames.push({ data, at: Date.now() }) });
await page.goto(SITE);
await page.waitForSelector('.spine');
const blank = frames.length;

const pause = ms => page.waitForTimeout(ms);
async function glide(locator) {
  await locator.scrollIntoViewIfNeeded();
  const box = await locator.boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 18 });
  await pause(180);
}
async function press(locator) {
  await glide(locator);
  await locator.click();
}

await pause(1300);
await press(page.getByRole('button', { name: 'Take the four-step tour' }));
await pause(500);

await glide(page.getByRole('searchbox'));
await page.getByRole('searchbox').pressSequentially('tr', { delay: 260 });
await page.waitForSelector('.result');
await pause(1500);

await press(page.getByRole('button', { name: 'Add filter' }));
await page.getByLabel('Choose a filter').selectOption('topic: trees');
await pause(1500);

const handle = await page.locator('.handle').boundingBox();
await page.mouse.move(handle.x + 14, handle.y + 60, { steps: 18 });
await pause(250);
await page.mouse.down();
for (let i = 1; i <= 40; i++) {
  await page.mouse.move(handle.x + 14 - i * 10, handle.y + 60);
  await pause(45);
}
await page.mouse.up();
await pause(1300);
await page.screenshot({ path: join(OUTPUT, 'walkthrough.png') });

const onDisk = page.locator('.spine[aria-label*="on disk"]').first();
await glide(onDisk);
await onDisk.evaluate(spine => spine.click());
await pause(1300);
await press(page.getByRole('button', { name: 'Open and bring to RAM' }));
// The button under the pointer becomes Delete, so move away from it
await page.mouse.move(size.width - 40, 300, { steps: 12 });
await pause(1400);

await press(page.getByRole('button', { name: /^Delete all/ }));
await pause(1300);
await press(page.locator('.strip button'));
await pause(1800);

await page.screencast.stop();
await browser.close();

// A frame only arrives when the page changes, so each one is held until the next
const shown = frames.slice(blank);
const list = shown.map(({ data, at }, i) => {
  writeFileSync(join(dir, `${i}.jpg`), data);
  return `file '${i}.jpg'\nduration ${((shown[i + 1]?.at ?? at + 1000) - at) / 1000}\n`;
});
writeFileSync(join(dir, 'frames.txt'), `${list.join('')}file '${shown.length - 1}.jpg'\n`);

execFileSync('ffmpeg', [
  '-y', '-loglevel', 'error', '-f', 'concat', '-i', join(dir, 'frames.txt'),
  '-vf', 'fps=10,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=64:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle',
  join(OUTPUT, 'walkthrough.gif'),
]);
rmSync(dir, { recursive: true });
