// Runs the parity script under TeaVM, then calls every export of the real engine.js.
import { writeFileSync } from 'node:fs';
import assert from 'node:assert/strict';

const target = process.argv[2];
const parity = await import(`${target}/parity.mjs`);
const trace = parity.runParity();
writeFileSync(`${target}/parity-teavm.txt`, trace);

// Anything else means the engine failed inside, which a rejected call would otherwise hide
const expectedErrors = [
  /^No document stored at that URI$/,
  /^There is nothing to undo( for that document)?$/,
  /^Doc being added can't exceed threshold for doc size$/,
  /^The limit can't go below the largest document, which is \d+ bytes$/,
  /^Limit cannot be less than 1$/,
  /^Document names use lowercase letters, digits and dashes$/,
  /^A document needs (a name|some text)$/,
  /^A document can hold up to \d+ characters$/,
  /^A word can be up to \d+ characters$/,
  /^Filters are written as key, tab, value$/,
];

let responses = 0;
let rejected = 0;
for (const line of trace.split('\n')) {
  if (line.startsWith('{')) {
    const response = JSON.parse(line);
    responses++;
    if (response.ok === false) {
      rejected++;
      assert.ok(expectedErrors.some(pattern => pattern.test(response.error)), `internal error: ${response.error}`);
    }
  }
}
console.log(`parsed ${responses} responses, ${rejected} rejected for expected reasons`);

const engine = await import(`${target}/engine.mjs`);
const call = (name, ...args) => {
  const response = JSON.parse(engine[name](...args));
  assert.equal(response.ok, true, `${name} failed: ${response.error}`);
  return response;
};

call('reset');
for (let i = 0; i < 6; i++) {
  call('putDocument', `doc-${i}`, `tree trie shared number${i}`);
  call('tagDocument', `doc-${i}`, 'topic', i % 2 ? 'trees' : 'heaps');
}
call('finishLoading');
assert.deepEqual(call('setMaxDocuments', 4).events.evicted, ['doc-0', 'doc-1']);
assert.equal(call('search', 'tree', 'topic\ttrees').result.length, 3);
assert.equal(call('searchPrefix', 'tr', '').result.length, 6);
assert.match(call('diskJson', 'doc-0').result ?? '', /"uri"|^$/);
assert.deepEqual(call('indexView', 'tr').result[1].next, ['r']);
call('getDocument', 'doc-0');
call('setMaxBytes', 500);
assert.equal(call('deleteDocument', 'doc-1').result, true);
assert.equal(call('undoDocument', 'doc-1').state.history.length, 0);
assert.equal(call('deleteAll', 'shared', '').result.length, 6);
assert.equal(call('undoLast').state.ramCount, 4);
assert.equal(call('deleteAllPrefix', 'tr', 'topic\theaps').result.length, 3);
assert.equal(call('snapshot').state.history.length, 1);
assert.equal(JSON.parse(engine.undoDocument('Bad Name')).ok, false);

// The shipped engine reads a real clock, so its times have to be real numbers
const timed = call('searchPrefix', 'tr', '');
assert.ok(Number.isFinite(timed.storeNanos) && timed.storeNanos >= 0, `bad time: ${timed.storeNanos}`);
assert.equal(JSON.parse(engine.undoDocument('Bad Name')).storeNanos, null);
assert.equal(call('search', '', '').storeNanos, null);
call('reset');
const times = [];
for (let i = 0; i < 2000; i++) {
  times.push(call('putDocument', `time-${i % 50}`, 'tree '.repeat(200)).storeNanos);
}
assert.ok(times.some(time => time > 0), 'every time was zero, so the clock is not running');
console.log(`store times read a real clock: largest ${Math.max(...times)} ns, ${new Set(times).size} distinct values`);
console.log('every export works from JavaScript');
