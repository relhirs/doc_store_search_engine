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
const clickSpine = (page, name) => page.locator('.spine', { hasText: name }).evaluate(spine => spine.click());
const pass = name => console.log(`ok: ${name}`);

const STOPS = [
  ['Welcome', null],
  ['The memory wall', 'wall'],
  ['Search', 'search', /store\.searchByPrefix\("tr"\)/],
  ['The word index', 'panel'],
  ['Ranked results', 'results'],
  ['The real call', 'strip'],
  ['Filters', 'filters', /store\.searchByPrefixAndMetadata\("tr", Map\.of\("topic", "trees"\)\)/],
  ['The memory limit', 'wall', /store\.setMaxDocumentCount\(8\)/],
  ['Eviction order', 'panel'],
  ['A document on disk', 'panel', /1 back in RAM/],
  ['Bulk delete', 'bulk', /store\.deleteAll/],
  // History's two deletes follow straight after the undo, so they're what the strip shows
  ['Undo', 'undo', /store\.delete\(uri/],
  ['History', 'panel', /store\.undo\(uri/],
  ["That's the tour", null],
];
const card = page => page.locator('.spotlight__card');
const main = page => page.locator('.spotlight__actions button').last();
const stopTitle = page => card(page).locator('h2').innerText();
const documents = async page => { const { ram, disk } = await tiers(page); return ram + disk; };

async function press(page) {
  await page.waitForTimeout(420);
  await main(page).click();
  await page.waitForTimeout(350);
}

// Everything a visitor needs from one stop: the box on the feature, the card clear of it, the arrow between them
async function checkStop(page, target) {
  const layout = await page.evaluate(name => {
    const rect = element => {
      const { left, top, right, bottom } = element.getBoundingClientRect();
      return { left, top, right, bottom };
    };
    const line = document.querySelector('.spotlight__arrow line');
    return {
      hole: rect(document.querySelector('.spotlight__hole')),
      card: rect(document.querySelector('.spotlight__card')),
      target: name ? rect(document.querySelector(`[data-tour="${name}"]`)) : null,
      arrow: line ? ['x1', 'y1', 'x2', 'y2'].map(key => Number(line.getAttribute(key))) : null,
      width: innerWidth,
      height: innerHeight,
      sideways: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    };
  }, target);
  const { hole, card: box, arrow, width, height } = layout;
  const label = `at ${await stopTitle(page)}`;
  assert.equal(layout.sideways, 0, `no sideways scroll ${label}`);
  assert.ok(box.left >= 0 && box.top >= 0 && box.right <= width && box.bottom <= height, `the card is on screen ${label}`);
  if (!target) {
    assert.equal(hole.right - hole.left, 0, `nothing is highlighted ${label}`);
    assert.equal(arrow, null, `no arrow ${label}`);
    return;
  }
  const inside = (point, low, high) => point >= low - 1 && point <= high + 1;
  assert.ok(hole.right > hole.left && hole.bottom > hole.top, `there is a box ${label}`);
  assert.ok(layout.target.left < hole.right && layout.target.right > hole.left && layout.target.top < hole.bottom && layout.target.bottom > hole.top, `the box is on the feature ${label}`);
  assert.ok(arrow, `there is an arrow ${label}`);
  const [, , x, y] = arrow;
  assert.ok(inside(x, hole.left, hole.right) && inside(y, hole.top, hole.bottom), `the arrow ends on the box ${label}`);
  const overlaps = box.left < hole.right && box.right > hole.left && box.top < hole.bottom && box.bottom > hole.top;
  const tooBig = hole.bottom - hole.top + box.bottom - box.top + 52 > height;
  assert.ok(!overlaps || tooBig, `the card leaves the box clear ${label}`);
}

async function allStops(options, name) {
  const page = await open(options);
  await page.getByRole('button', { name: 'Take the tour' }).click();
  for (const [i, [title, target, expected]] of STOPS.entries()) {
    await page.waitForTimeout(320);
    assert.equal(await stopTitle(page), title);
    assert.match(await card(page).innerText(), new RegExp(`${i + 1} of ${STOPS.length}`));
    await checkStop(page, target);
    if (i === STOPS.length - 1) {
      break;
    }
    assert.equal(await main(page).innerText(), expected ? 'Do it for me' : 'Next');
    assert.equal(await page.getByRole('button', { name: 'End tour' }).count(), 1);
    await press(page);
    if (expected) {
      assert.match(await strip(page), expected, `${title} made its real call`);
    }
  }
  await page.getByRole('button', { name: 'Close' }).click();
  assert.equal(await card(page).count(), 0);
  await page.getByRole('searchbox').fill('binary');
  await page.waitForTimeout(400);
  const shown = await page.locator('.result').count();
  assert.match(await strip(page), new RegExp(`\\b${shown} results?\\b`), 'search works after the tour');
  await page.context().close();
  pass(`every stop of the tour works with Next alone, ${name}`);
}

async function tourByHand() {
  const page = await open();
  await page.getByRole('button', { name: 'Take the tour' }).click();
  await press(page);
  await press(page);

  // Dimmed parts can't be reached, by pointer or by keyboard
  const reset = await page.getByRole('button', { name: 'Reset' }).boundingBox();
  await page.mouse.click(reset.x + 5, reset.y + 5);
  await page.waitForTimeout(200);
  assert.equal(await stopTitle(page), 'Search');
  assert.equal(await page.evaluate(([x, y]) => document.elementFromPoint(x, y).className, [reset.x + 5, reset.y + 5]), 'spotlight__block');
  for (let i = 0; i < 12; i++) {
    await page.keyboard.press('Tab');
    const reachable = await page.evaluate(() => {
      const active = document.activeElement;
      return active === document.body || !!active.closest('.spotlight__card, [data-tour="search"]');
    });
    assert.ok(reachable, 'Tab stays on the card and the feature');
  }

  await page.getByRole('searchbox').fill('tr');
  await page.waitForTimeout(400);
  assert.equal(await main(page).innerText(), 'Next');
  assert.equal(await page.locator('.spotlight__task').count(), 0);
  await press(page);
  await press(page);
  await press(page);
  await press(page);

  await page.getByRole('button', { name: 'Add filter' }).click();
  await page.getByLabel('Choose a filter').selectOption('topic: trees');
  await page.waitForTimeout(450);
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);

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
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);
  await press(page);

  assert.match(await page.locator('.json').innerText(), /"uri": "http:\/\/demo\.local\//);
  await page.getByRole('button', { name: 'Open and bring to RAM' }).click();
  await page.waitForTimeout(600);
  assert.match(await strip(page), /1 moved to disk, 1 back in RAM/);
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);

  await page.getByRole('button', { name: /^Delete all/ }).click();
  await page.waitForTimeout(400);
  assert.equal(await documents(page), 16);
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);

  await page.locator('.strip button').click();
  await page.waitForTimeout(500);
  assert.equal(await documents(page), 24);
  assert.equal(await page.locator('.result').count(), 8, 'rows come back with their documents');
  assert.match(await strip(page), /8 restored/);
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);

  assert.equal(await stopTitle(page), 'History');
  await page.waitForTimeout(300);
  assert.equal(await documents(page), 22, 'the tour deleted two documents');
  const undos = page.locator('.history .chip');
  const older = (await undos.last().innerText()).replace('Undo ', '');
  const newer = (await undos.first().innerText()).replace('Undo ', '');
  assert.notEqual(older, newer);
  await undos.last().click();
  await page.waitForTimeout(400);
  assert.equal(await page.locator('.spine', { hasText: new RegExp(`^${older}$`) }).count(), 1);
  assert.equal(await page.locator('.spine', { hasText: new RegExp(`^${newer}$`) }).count(), 0);
  assert.equal(await main(page).innerText(), 'Next');
  await press(page);
  assert.equal(await stopTitle(page), "That's the tour");
  await page.context().close();
  pass('every try-it stop works by hand');
}

async function tourControls() {
  const page = await open();
  await page.getByRole('button', { name: 'Take the tour' }).click();
  for (let i = 0; i < 10; i++) {
    await press(page);
  }
  assert.equal(await stopTitle(page), 'Bulk delete');

  // The second press of a double click must not reach the next stop's button
  await page.waitForTimeout(420);
  await main(page).click();
  await page.waitForTimeout(90);
  await main(page).click().catch(() => {});
  await page.waitForTimeout(500);
  assert.equal(await stopTitle(page), 'Undo');
  assert.equal(await documents(page), 16, 'one delete and no undo');

  await page.waitForTimeout(420);
  await main(page).click();
  await page.waitForTimeout(90);
  await main(page).click().catch(() => {});
  await page.waitForTimeout(500);
  assert.equal(await stopTitle(page), 'History');
  assert.equal(await documents(page), 22, 'one undo, then the two deletes');

  await page.waitForTimeout(400);
  await page.getByRole('button', { name: 'Back' }).click();
  await page.waitForTimeout(420);
  assert.equal(await stopTitle(page), 'Undo');
  await main(page).click();
  await page.waitForTimeout(420);
  assert.equal(await stopTitle(page), 'History');
  assert.equal(await documents(page), 22, "Back doesn't repeat the deletes");

  await page.keyboard.press('Escape');
  assert.equal(await card(page).count(), 0, 'Esc ends the tour');
  assert.equal(await page.evaluate(() => document.querySelectorAll('[inert]').length), 0, 'the page opens up again');

  await page.getByRole('button', { name: 'Tour', exact: true }).click();
  await page.waitForTimeout(900);
  assert.equal(await stopTitle(page), 'Welcome');
  assert.deepEqual(await tiers(page), { ram: 16, disk: 8 }, 'the tour starts from a clean store');
  await page.context().close();
  pass('double presses, Back, Esc and restarting all work');
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
  await page.getByRole('button', { name: 'Tour', exact: true }).click();
  await page.waitForTimeout(500);
  await main(page).click();
  await page.waitForTimeout(60);
  const hole = await page.locator('.spotlight__hole').boundingBox();
  const wall = await page.locator('[data-tour="wall"]').boundingBox();
  assert.ok(Math.abs(hole.y - (wall.y - 6)) < 2, 'the box reaches the next stop at once');
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
  await allStops({ viewport: { width: 1440, height: 900 } }, 'on a desktop');
  await allStops({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true, deviceScaleFactor: 2 }, 'on a phone');
  await tourByHand();
  await tourControls();
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
