# Demo engine

The real Stage 6 store, compiled to JavaScript by TeaVM, with a small JSON facade for the web demo. Each visitor gets a private store in their own browser.

    ./verify.sh

This runs the tests, then runs one fixed sequence on the JVM and under TeaVM and requires byte-identical responses. It needs JDK 17, Maven and Node. The output is `target/javascript/engine.js`, an ES module.

## Calling it

Every export returns a JSON string. Documents are named with lowercase letters, digits and dashes, and the engine turns that name into a URI. Filters are one `key\tvalue` pair per line, or an empty string.

| Export | Does |
|---|---|
| `reset()` | Starts a new, empty store |
| `putDocument(name, text)` | Adds or replaces a document. Text is limited to 20,000 characters, and a word to 200 |
| `tagDocument(name, key, value)` | Sets a metadata value |
| `finishLoading()` | Keeps everything so far out of the visitor's history and undo |
| `getDocument(name)` | Reads a document, which counts as a use |
| `deleteDocument(name)` | Deletes a document |
| `undoLast()` | Undoes the latest action |
| `undoDocument(name)` | Undoes the latest action on one document |
| `search(keyword, filters)` | Exact word search, ranked by count |
| `searchPrefix(prefix, filters)` | Prefix search, ranked by count |
| `deleteAll(keyword, filters)` | Bulk delete by word |
| `deleteAllPrefix(prefix, filters)` | Bulk delete by prefix |
| `setMaxDocuments(limit)` | Sets the document limit |
| `setMaxBytes(limit)` | Sets the byte limit, which can't go below the largest document |
| `snapshot()` | Current state, with no effect on the store |
| `diskJson(name)` | Stored JSON for a document on disk, or null. No effect on the store |
| `indexView(text)` | The word index along `text`, one level per letter it can follow. No effect on the store |

A blank search or delete with no filters is never sent to the store. With filters, it runs on the filters alone.

## Responses

```json
{
  "ok": true,
  "call": "store.searchByPrefix(\"tr\")",
  "storeNanos": 184000,
  "result": [{ "name": "b-trees", "count": 7, "text": "...", "metadata": { "topic": "trees" } }],
  "events": { "evicted": [], "loaded": [], "added": [], "deleted": [] },
  "state": {
    "ram": [{ "name": "b-trees", "bytes": 412, "metadata": {} }],
    "disk": [],
    "nextToEvict": "b-trees",
    "ramCount": 1,
    "ramBytes": 412,
    "maxDocuments": -1,
    "maxBytes": -1,
    "history": [{ "label": "store.delete(uri(\"a\"))", "bulk": false, "targets": ["a"] }]
  }
}
```

A rejected call has `"ok": false` and an `error` to show the visitor, in place of `result`. It still has `events` and `state`.

- **`storeNanos`**: how long the store call took, with the bridge's own work left out. It's `null` if the call never reached the store: a blank search, or a call the bridge rejected. A call the store rejected still has a time.
- **`events`**: the documents that changed tier. `evicted` and `loaded` are names, in the order the engine moved them. `added` and `deleted` are `{ "name", "tier" }`, in name order. Documents that moved and came back during one call don't appear.
- **`state.ram`**: least recently used first. Documents returned by one search share a last use time, so use `nextToEvict` for the next victim instead of the first tile.
- **`state.history`**: most recent first.

`storeNanos` is the one value that isn't the same on every run, so only `DemoEngine` gives `Engine` a real clock. Everywhere else, including the parity script, the clock is constant and times are `0`, which is what lets the two platforms be compared byte for byte. Browsers coarsen their timers, so show anything under a millisecond as "under 1 ms".

`indexView` returns levels like `{ "prefix": "tr", "next": ["a", "e", "i"], "documents": 0 }`. The first is the root, and the list stops at the first letter the index can't follow. `documents` counts the documents with exactly that word.
