// Drives the production build in a real browser. Start `npm run preview` first, then `npm run e2e`.
import { globSync } from 'node:fs';
import { homedir } from 'node:os';
import assert from 'node:assert/strict';
import { chromium } from 'playwright-core';

const SITE = process.env.SITE ?? 'http://localhost:4173/doc_store_search_engine/';
const executablePath = process.env.CHROME_PATH ?? globSync(
  `${homedir()}/Library/Caches/ms-playwright/chromium-*/*/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing`,
)[0];

const browser = await chromium.launch({ executablePath, headless: true });
const problems = [];

async function open(options = { viewport: { width: 1440, height: 900 } }) {
  const context = await browser.newContext(options);
  const page = await context.newPage();
  page.on('console', message => {
    if (['error', 'warning'].includes(message.type())) problems.push(`${message.type()}: ${message.text()}`);
  });
  page.on('pageerror', error => problems.push(`page error: ${error.message}`));
  await page.goto(SITE);
  await page.waitForSelector('.spine');
  await page.waitForTimeout(1000);
  return page;
}

const tiers = page => page.locator('.spine').evaluateAll(spines => {
  const labels = spines.map(spine => spine.getAttribute('aria-label'));
  return { ram: labels.filter(label => label.includes('in RAM')).length, disk: labels.filter(label => label.includes('on disk')).length };
});
const strip = page => page.locator('.strip__line').innerText();
const tourStep = page => page.locator('.tour h2').innerText();
const clickSpine = (page, name) => page.locator('.spine', { hasText: name }).evaluate(spine => spine.click());
const pass = name => console.log(`ok: ${name}`);

async function fullTour() {
  const page = await open();
  assert.deepEqual(await tiers(page), { ram: 16, disk: 8 });
  await page.getByRole('button', { name: 'Take the four-step tour' }).click();

  await page.getByRole('searchbox').fill('tr');
  await page.waitForSelector('.result');
  await page.waitForTimeout(300);
  assert.match(await strip(page), /store\.searchByPrefix\("tr"\)/);
  assert.match(await strip(page), /16 results/);
  assert.match(await strip(page), /(under 1|\d+) ms/);
  assert.match(await tourStep(page), /Step 2/);

  await page.getByRole('button', { name: 'Add filter' }).click();
  await page.getByLabel('Choose a filter').selectOption('topic: trees');
  await page.waitForTimeout(450);
  assert.match(await tourStep(page), /Step 3/);

  const handle = await page.locator('.handle').boundingBox();
  await page.mouse.move(handle.x + 14, handle.y + 60);
  await page.mouse.down();
  for (let i = 1; i <= 20; i++) {
    await page.mouse.move(handle.x + 14 - i * 20, handle.y + 60);
    await page.waitForTimeout(16);
  }
  await page.mouse.up();
  await page.waitForTimeout(700);
  assert.equal((await tiers(page)).ram, 8);

  await page.locator('.spine[aria-label*="on disk"]').first().evaluate(spine => spine.click());
  await page.waitForTimeout(200);
  assert.match(await page.locator('.json').innerText(), /"uri": "http:\/\/demo\.local\//);
  await page.getByRole('button', { name: 'Open and bring to RAM' }).click();
  await page.waitForTimeout(600);
  assert.match(await strip(page), /1 moved to disk, 1 back in RAM/);
  assert.match(await tourStep(page), /Step 4/);

  await page.getByRole('button', { name: /^Delete all/ }).click();
  await page.waitForTimeout(400);
  assert.equal((await tiers(page)).ram + (await tiers(page)).disk, 16);
  await page.locator('.strip button').click();
  await page.waitForTimeout(500);
  assert.equal((await tiers(page)).ram + (await tiers(page)).disk, 24);
  assert.equal(await page.locator('.result').count(), 8, 'rows come back with their documents');
  assert.match(await strip(page), /8 restored/);

  await clickSpine(page, 'hash-tables');
  await page.waitForTimeout(150);
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.waitForTimeout(300);
  await clickSpine(page, 'stacks');
  await page.waitForTimeout(150);
  await page.getByRole('button', { name: 'Delete', exact: true }).click();
  await page.waitForTimeout(300);
  await page.getByRole('tab', { name: 'History' }).click();
  await page.getByRole('button', { name: 'Undo hash-tables' }).click();
  await page.waitForTimeout(400);
  assert.equal(await page.locator('.spine', { hasText: 'hash-tables' }).count(), 1);
  assert.equal(await page.locator('.spine', { hasText: 'stacks' }).count(), 0);
  assert.match(await page.locator('.tour').innerText(), /That's the tour/);
  await page.context().close();
  pass('the full tour runs to its end');
}

async function wall() {
  const page = await open();
  const clipped = await page.locator('.spine span').evaluateAll(names => names.filter(name => name.parentElement.scrollHeight > name.parentElement.clientHeight + 1).length);
  assert.equal(clipped, 0, 'every name fits on its spine');

  const next = (await page.locator('.spine--next').innerText()).trim();
  const ramNames = await page.locator('.spine[aria-label*="in RAM"]').evaluateAll(spines =>
    [...spines].sort((a, b) => a.getBoundingClientRect().x - b.getBoundingClientRect().x).map(spine => spine.textContent.trim()));
  assert.equal(ramNames.at(-1), next, 'the next to leave sits beside the divider');
  await page.locator('.handle').focus();
  await page.keyboard.press('ArrowLeft');
  await page.waitForTimeout(500);
  assert.equal((await page.locator('.spine[aria-label*="on disk"]').first().innerText()).trim(), next, 'the marked tile leaves first');

  const positions = () => page.locator('.spine[aria-label*="in RAM"]').evaluateAll(spines => spines.map(spine => spine.getBoundingClientRect().x));
  const before = await positions();
  await page.keyboard.press('End');
  await page.waitForTimeout(600);
  assert.deepEqual(await positions(), before, 'raising the limit moves nothing');
  assert.equal(await page.locator('.slot').count(), 9);
  const right = await page.locator('.spine').evaluateAll(spines => Math.max(...spines.map(spine => spine.getBoundingClientRect().right)));
  assert.ok(right <= 1416.5, `tiles stay on the wall, reached ${right}`);

  await page.keyboard.press('Home');
  await page.waitForTimeout(1600);
  assert.deepEqual(await tiers(page), { ram: 1, disk: 23 });
  await page.getByRole('button', { name: 'Reset' }).click();
  await page.waitForTimeout(1100);
  assert.deepEqual(await tiers(page), { ram: 16, disk: 8 });
  await page.context().close();
  pass('the wall follows the engine, by keyboard too');
}

async function input() {
  const page = await open();
  const search = page.getByRole('searchbox');

  await search.fill('tr ');
  await page.waitForTimeout(350);
  assert.match(await strip(page), /store\.searchByPrefix\("tr"\)/, 'spaces around a word are not sent');

  await search.fill('bayer');
  await page.waitForTimeout(450);
  assert.match(await page.locator('.empty').innerText(), /Try Bayer/);

  for (const text of ['日本', '😀', '"', '\\', '(.*', 'a'.repeat(100_000)]) {
    await search.fill(text);
    await page.waitForTimeout(250);
    assert.notEqual(await page.locator('.strip__error').count(), 1, `rejected: ${text.slice(0, 10)}`);
  }

  await search.fill('');
  await search.pressSequentially('traversal', { delay: 5 });
  await page.waitForTimeout(400);
  assert.match(await strip(page), /store\.searchByPrefix\("traversal"\)/, 'fast typing lands on the latest text');

  await page.getByRole('button', { name: 'Add a document' }).click();
  await page.getByLabel('Name').fill('日本語');
  assert.match(await page.locator('.add__hint').innerText(), /at least one letter/);
  await page.locator('.add textarea').fill('some tree words');
  await page.getByRole('button', { name: 'Add document' }).click();
  await page.waitForTimeout(200);
  assert.match(await page.locator('.add__error').innerText(), /needs a name/);
  await page.getByLabel('Name').fill('My Notes_v2.txt ');
  assert.equal(await page.locator('.add__error').count(), 0, 'editing clears the error');
  assert.match(await page.locator('.add__hint').innerText(), /Saved as my-notes-v2-txt$/);
  await page.locator('.add textarea').fill('a'.repeat(20_000));
  await page.getByRole('button', { name: 'Add document' }).click();
  await page.waitForTimeout(200);
  assert.match(await page.locator('.add__error').innerText(), /A word can be up to 200 characters/);
  await page.locator('.add textarea').fill('word '.repeat(10_000));
  await page.getByRole('button', { name: 'Add document' }).click();
  await page.waitForTimeout(200);
  assert.match(await page.locator('.add__error').innerText(), /up to 20000 characters/);
  await page.locator('.add textarea').fill('some tree words');
  await page.getByRole('button', { name: 'Add document' }).click();
  await page.waitForTimeout(350);
  assert.equal(await page.locator('.spine', { hasText: 'my-notes-v2-txt' }).count(), 1);
  await page.context().close();
  pass('awkward input is handled');
}

async function workerAlwaysAnswers() {
  const page = await open();
  const reply = await page.evaluate(() => new Promise((resolve, reject) => {
    const url = performance.getEntriesByType('resource').find(entry => entry.name.includes('/worker-')).name;
    const worker = new Worker(url, { type: 'module' });
    worker.onmessage = ({ data }) => (data.ready ? worker.postMessage({ id: 1, op: 'noSuchExport', args: [] }) : resolve(JSON.parse(data.json)));
    setTimeout(() => reject(new Error('the worker never answered')), 3000);
  }));
  assert.equal(reply.ok, false);
  assert.match(reply.error, /failed on that call/);
  assert.ok(reply.state.ram, 'the failure still reports the state');
  await page.context().close();
  pass('a failing call still gets an answer');
}

async function reducedMotion() {
  const page = await open({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
  await page.locator('.handle').focus();
  await page.keyboard.press('ArrowLeft');
  await page.waitForTimeout(60);
  const colors = await page.locator('.spine[aria-label*="on disk"]').evaluateAll(spines => [...new Set(spines.map(spine => getComputedStyle(spine).backgroundColor))]);
  assert.deepEqual(colors, ['rgb(143, 94, 0)']);
  await page.context().close();
  pass('reduced motion makes changes instant');
}

async function phone() {
  const page = await open({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 2 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), 0);
  assert.equal(await page.locator('.handle').count(), 0);
  assert.equal(await page.locator('.panel').isVisible(), false, 'the engine section starts closed');
  const clipped = await page.locator('.spine span').evaluateAll(names => names.filter(name => name.parentElement.scrollHeight > name.parentElement.clientHeight + 1).length);
  assert.equal(clipped, 0, 'every name fits on its spine');
  await page.getByRole('button', { name: 'Lower the memory limit' }).tap();
  await page.waitForTimeout(500);
  assert.deepEqual(await tiers(page), { ram: 15, disk: 9 });
  await page.locator('.spine[aria-label*="on disk"]').first().tap();
  await page.waitForTimeout(400);
  assert.ok(await page.locator('.json').isVisible(), 'tapping a tile shows its JSON');
  await page.context().close();
  pass('the phone layout works');
}

async function engineFailure() {
  for (const [reducedMotion, file] of [['no-preference', /walkthrough\.gif$/], ['reduce', /walkthrough\.png$/]]) {
    const context = await browser.newContext({ reducedMotion });
    const page = await context.newPage();
    await page.route('**/worker-*.js', route => route.abort());
    await page.goto(SITE);
    await page.waitForSelector('.failed', { timeout: 5000 });
    assert.match(await page.locator('.failed').innerText(), /didn't load/);
    const recording = page.locator('.failed img');
    await recording.evaluate(image => image.decode());
    assert.match(await recording.evaluate(image => image.currentSrc), file);
    await context.close();
  }
  pass('a failed engine shows the fallback page and a recording');
}

try {
  await fullTour();
  await wall();
  await input();
  await workerAlwaysAnswers();
  await reducedMotion();
  await phone();
  await engineFailure();
  assert.deepEqual(problems, [], 'the console stayed clean');
  pass('no console errors or warnings');
} finally {
  await browser.close();
}
