# Document Store / Search Engine — Project Overview

This file is a detailed, LLM-readable summary of this project's purpose, architecture, and technical
depth. It exists specifically so that an LLM (or a human) can read it and extract accurate,
specific bullet points for a resume, portfolio, or project-level write-up. It is not meant to be
exhaustive documentation of every method — it focuses on *what the system does*, *how it works
internally*, and *what is technically interesting about it*.

## What this project is

A custom, in-memory/on-disk **document store and search engine**, built in Java as a
multi-stage semester project (Yeshiva University, COM 1320 - Data Structures). Each `stageN`
folder is a successive, cumulative iteration of the same system, each adding a new capability on
top of the last. **Stage 6 is the final, most complete version** and is the one to describe when
summarizing "what the project does."

At its core, it is a `DocumentStore`: a system that lets a client `put`/`get`/`delete` documents
(text or binary) identified by a `URI`, and then **search across all stored documents by keyword or
prefix**, with results ranked by relevance. On top of that, it adds metadata-based filtering,
full multi-level undo, and a two-tier (RAM + disk) memory management system with configurable
limits and automatic eviction — i.e., it behaves like a miniature, embeddable search-engine /
document-database hybrid, similar in spirit to a simplified Lucene + key-value store, but
with its index, storage, eviction queue, and undo history built on custom data structures rather
than off-the-shelf libraries.

## Core capabilities (Stage 6, final version)

- **Document storage**: store plain text or binary documents, keyed by `URI`, via `put`/`get`/`delete`.
- **Keyword search**: `search(keyword)` returns all documents containing an exact word, ranked by
  how many times the word appears in each document (most relevant first).
- **Prefix search**: `searchByPrefix(prefix)` returns all documents containing *any* word that
  starts with the given prefix, ranked by combined occurrence count of all matching words.
- **Metadata search**: arbitrary key/value metadata can be attached to any document; documents can
  be queried by metadata alone, or by keyword/prefix **combined with** metadata filters
  (`searchByKeywordAndMetadata`, `searchByPrefixAndMetadata`).
- **Bulk/targeted deletion**: delete all documents matching a keyword, a prefix, a metadata filter,
  or any combination thereof, in one call.
- **Full undo history**: every mutating operation (put, delete, setMetadata, bulk delete)
  is undoable, including undo of a specific document's last action by URI, backed by a custom
  command-pattern stack (`Undoable`, `GenericCommand`, `CommandSet`).
  - `undo()` reverses the single most recent action system-wide.
  - `undo(uri)` walks back through history to reverse the most recent action for one specific
    document, without disturbing other documents' history — implemented by draining and
    rebuilding the command stack while searching for a matching command.
- **Two-tier memory management (RAM + disk)**:
  - The store can be configured with a maximum document count and/or a maximum total byte size
    held in memory at once (`setMaxDocumentCount`, `setMaxDocumentBytes`).
  - When a limit is exceeded, the **least-recently-used** document is automatically evicted from
    RAM to disk (serialized to JSON), and transparently reloaded into RAM on next access — a
    simplified LRU cache / virtual-memory-style design.
  - "Last used" time is tracked per document and updated on every get, search hit, or metadata
    write, so eviction order reflects real usage, not just insertion order. Reads the caller
    didn't ask for, such as checking a document against a metadata filter, don't count as use and
    leave a document on disk where it is.
  - Use times come from a strictly increasing clock (`UseClock`), so separate uses never share a
    time. A search is one use: all of its results get the same time.

## How search ranking works

Relevance ranking is **word-frequency based**, not a general TF-IDF/BM25 model:
- Exact keyword search ranks matches by the raw count of that exact word's occurrences in each
  document, descending.
- Prefix search ranks matches by the **summed occurrence count of every distinct word in the
  document that starts with the given prefix** (e.g., searching "pre" on a doc containing "prefix"
  10 times and "prepare" 3 times scores it 13), descending.
- Documents with equal counts are ordered by URI, so results are always in a stable order.
- Search is case-sensitive by design.
- Ranking is performed with custom `Comparator<URI>` instances passed into the trie's traversal/sort
  methods — the trie itself is ranking-agnostic; callers supply the ordering logic, which keeps the
  ranking strategy decoupled from the indexing structure. Each document is scored once per search,
  and scoring doesn't count as using it.
- Every word-count lookup for ranking happens against a per-document `HashMap<String,Integer>`
  word-frequency map (`wordMap`) that's built once when a document is ingested (tokenized on
  ingestion) and persisted/deserialized alongside the document, so re-ranking never requires
  re-tokenizing text.

## How indexing works

- **Trie (prefix tree)** — the primary search index. Each distinct word found in any document is
  inserted into a trie, where each trie node maps to the **set of URIs** of documents containing
  that word. This gives:
  - O(word length) exact-match lookup for `search`.
  - Efficient prefix enumeration for `searchByPrefix` by walking to the prefix node and collecting
    all URIs stored at and below it.
  - The trie supports sorted retrieval by accepting a caller-supplied `Comparator`, so the same
    index structure powers both exact and prefix search with different ranking rules.
  - Deleting a document removes it from every trie node for every word it contained, keeping the
    index consistent with storage on delete/undo.
- **B-Tree** — the primary key-value storage structure mapping `URI -> Document`, used instead of a
  plain hash map specifically because it supports **out-of-core (disk) storage**: when a document is evicted from RAM, the B-Tree node's entry for that URI can hand off
  to the persistence manager to serialize the value to disk and only keep the key resident,
  reloading the value transparently when accessed again. Implemented as a genuine disk-aware
  B-tree (order 4 internally), not just wrapped around a `TreeMap`. Deleting a key leaves an empty
  entry in place rather than rebalancing the tree.
- **Min-Heap** — used as the eviction-priority queue for the LRU memory manager. Each entry wraps a
  document's URI and compares by `lastUseTime`, so `remove()` always pops the least-recently-used
  document in O(log n), and `reHeapify()` lets the store re-prioritize a document immediately after
  it's touched, without rebuilding the heap.
- **Custom Stack** — backs the undo history (LIFO command stack), supporting both single-step undo
  and the search for a specific document's history among other interleaved commands.
- **Custom HashTable** — used in Stages 2 to 5 and replaced by the B-Tree in Stage 6 to support disk
  paging. It isn't part of the final version.

The `Trie`, `BTree`, `MinHeap`, and `Stack` are **custom structures** — no
`java.util.TreeMap`/`PriorityQueue`/etc. under the hood — as the explicit point of the course was
learning to build and reason about these data structures directly. Java's `HashMap` and `HashSet`
are still used for smaller jobs: per-document word counts and metadata, the trie's per-word URI
sets, the heap's element-to-index lookup, and tracking which documents are in RAM.

## Persistence / disk tier

- `DocumentPersistenceManager` serializes evicted documents to **JSON** on disk (via Gson), storing
  the document's text/binary content (binary is Base64-encoded), its full word-frequency map, and
  its metadata, so a document can be fully reconstructed — including search ranking data — without
  re-parsing the original text.
- Each document is written to a file path derived deterministically from its URI, under a
  configurable base directory. The `http://` scheme is stripped, `https://` documents go in an
  `https/` folder so the two never share a file, and `..` in a URI is encoded so it can't leave the
  base directory.
- Deserialization rebuilds a `Document` object and restores its metadata entries individually.

## Error handling / contract design

- Strict input validation throughout: `IllegalArgumentException` for null/blank URIs, null formats,
  missing keys, or operating on URIs with no stored document.
- Deterministic return-value contracts, e.g. `put` returns `0` for a brand-new document, the old
  document's `hashCode()` if it replaced an existing one, and also uses a `null` input stream as an
  explicit "delete" signal with the same hashCode-return convention — a deliberately compact,
  dual-purpose API surface that was specified by the course project rather than invented ad hoc, and
  implemented to the letter of that spec.

## Tools & technologies

- **Language**: Java 17 (Stage 1's `pom.xml` targets Java 21)
- **Build system**: Maven (`pom.xml` per stage)
- **Testing**: JUnit 5 (Jupiter) — unit tests for every custom data structure and every
  DocumentStore stage (`HashTableImplTest`, `TrieImplTest`, `BTreeImplTest`, `MinHeapImplTest`,
  `StackImplTest`, `DocumentImplTest`, `DocumentStoreImplTest`, `DocumentPersistenceManagerTest`).
  Stage 6 has 162 tests. `MemoryTierTest` covers RAM and disk behaviour directly, including a
  randomized run that compares the store to a simple model after every operation, and
  `BTreeImplTest` checks every key after every insert across shuffled insertion orders.
- **Serialization**: Gson, with custom `JsonSerializer`/`JsonDeserializer` implementations for the
  `Document` type (needed because documents can hold either text or binary content plus metadata
  and a word-frequency index, which Gson can't map automatically).
- **Design patterns used deliberately**:
  - Command pattern for undo (`Undoable`, `GenericCommand`, `CommandSet`).
  - Strategy pattern via injected `Comparator`s to decouple ranking logic from the trie's traversal.
  - Two-tier cache/eviction pattern (RAM-resident set + min-heap priority queue + disk fallback),
    conceptually similar to how a database buffer pool or OS virtual memory manager works.

## Project structure (why there are 6 "stages")

The project was built incrementally across a semester, with each stage adding a layer of
functionality on the previous one's codebase:
1. **Stage 1** — basic `Document`/`DocumentStore` with simple put/get/delete.
2. **Stage 2** — introduces a custom `HashTable` as the backing store.
3. **Stage 3** — adds the undo command stack, including undo by URI.
4. **Stage 4** — adds the `Trie` search index, keyword/prefix search and ranked results, metadata
   search, and bulk/targeted deletion by keyword/prefix/metadata.
5. **Stage 5** — adds the `MinHeap`-based LRU tracking and in-memory document/byte limits
   (single-tier memory management, no disk paging yet).
6. **Stage 6** — swaps the backing store for a custom on-disk-capable `BTree`, adds the
   `DocumentPersistenceManager` (Gson-based JSON serialization), and completes the two-tier
   RAM/disk memory management described above.

When summarizing this project (e.g., for a resume), Stage 6 is the complete, representative
picture: a search engine and document store with ranked keyword/prefix search, metadata filtering,
multi-level undo, and automatic two-tier RAM/disk memory management, built on custom data
structures (trie, B-tree, min-heap, stack).
