import type { Response } from '../engine/types';
import type { Demo } from './useDemo';

export interface TourTask {
  text: string;
  done: (response: Response) => boolean;
  /** Makes the same engine call the visitor would have made, so later stops still have something to show. */
  perform: (demo: Demo) => void;
}

export interface TourStop {
  title: string;
  body: string;
  /** The data-tour name of the feature to highlight. Null centres the card. */
  target: string | null;
  /** Runs on every arrival, so it can only change what's on screen. */
  show?: (demo: Demo) => void;
  /** Runs on the first arrival only, so Back doesn't repeat it. */
  setup?: (demo: Demo) => void;
  task?: TourTask;
}

const called = (response: Response, start: string) => response.ok && !!response.call?.startsWith(start);

const showTab = (tab: Demo['tab']) => (demo: Demo) => {
  demo.setTab(tab);
  demo.setEngineOpen(true);
};

/** The tour only moves when the visitor presses a button, so they can see what each action did. */
export const STOPS: TourStop[] = [
  {
    title: 'Welcome',
    body: "This is a search engine and document store from a Java course project. The real Java runs in this tab, compiled to JavaScript, so nothing you do reaches a server. This tour explains each part. Some stops ask you to try something, and you can always press Do it for me.",
    target: null,
  },
  {
    title: 'The memory wall',
    body: "Every bar is one document, and its height shows its size. Blue bars are in RAM and brown bars are on disk. RAM only holds so many documents, so the rest wait on disk as JSON. The white mark shows which document leaves next.",
    target: 'wall',
  },
  {
    title: 'Search',
    body: 'Search looks through the text of every document. It is case sensitive. Prefix mode matches the start of a word, so tr finds tree and trie.',
    target: 'search',
    task: {
      text: 'Type tr in the search box.',
      done: response => called(response, 'store.searchByPrefix("tr")'),
      perform: demo => {
        demo.setMode('prefix');
        demo.setQuery('tr');
      },
    },
  },
  {
    title: 'The word index',
    body: "Words are indexed in a trie, a tree with one node per letter. Each row is a node on the path you typed, and its letters are the branches that exist right now. Search follows that path, so it doesn't have to read every document.",
    target: 'panel',
    show: showTab('index'),
  },
  {
    title: 'Ranked results',
    body: "Results rank by how many matching words each document holds. The bar under a result is blue if that document is in RAM and brown if it's on disk. Searching counts as using a document, so it can pull documents back from disk and push others out.",
    target: 'results',
  },
  {
    title: 'The real call',
    body: 'This strip shows the last real call to the Java store and what it did. The time covers only that store call. Browser clocks are coarse, so fast calls read as under 1 ms.',
    target: 'strip',
  },
  {
    title: 'Filters',
    body: 'Documents can carry metadata tags, such as topic and era. A filter narrows the search by tag, and the store does both in one call.',
    target: 'filters',
    task: {
      text: 'Press Add filter and choose topic: trees.',
      done: response => called(response, 'store.searchByPrefixAndMetadata("tr", Map.of("topic", "trees"))'),
      perform: demo => demo.setFilters([...demo.filters.filter(filter => filter.key !== 'topic'), { key: 'topic', value: 'trees' }]),
    },
  },
  {
    title: 'The memory limit',
    body: 'The divider marks how many documents fit in RAM. When the limit drops, the store evicts the least recently used documents and writes them to disk as JSON.',
    target: 'wall',
    task: {
      text: 'Drag the divider to the left, or press the minus button on a phone.',
      done: response => called(response, 'store.setMaxDocumentCount') && !!response.events?.evicted.length,
      perform: demo => demo.setLimit(Math.max((demo.state?.maxDocuments ?? 16) - 8, 1)),
    },
  },
  {
    title: 'Eviction order',
    body: "A min-heap ordered by last use decides what leaves RAM next, so the store doesn't have to scan every document. This list is the real queue. Documents returned by one search share a last use, so among those, the heap picks.",
    target: 'panel',
    show: showTab('eviction'),
  },
  {
    title: 'A document on disk',
    body: "This document is on disk, so you can see its stored JSON. Reading it here doesn't count as a use. Opening it is a real get: it comes back to RAM, and because RAM is full, another document has to leave.",
    target: 'panel',
    show: showTab('storage'),
    setup: demo => {
      const first = demo.state?.disk[0];
      if (first) {
        demo.selectTile(first.name);
      }
    },
    task: {
      text: 'Press Open and bring to RAM.',
      done: response => called(response, 'store.get') && !!response.events?.loaded.length,
      perform: demo => {
        const onDisk = demo.state?.disk ?? [];
        const name = onDisk.find(tile => tile.name === demo.selected)?.name ?? onDisk[0]?.name;
        if (name) {
          demo.openDocument(name);
        }
      },
    },
  },
  {
    title: 'Bulk delete',
    body: 'Delete all removes every result in one call. It is safe to try, because the next stop brings them back.',
    target: 'bulk',
    task: {
      text: 'Press Delete all.',
      done: response => called(response, 'store.deleteAll') && !!response.events?.deleted.length,
      perform: demo => demo.deleteResults(),
    },
  },
  {
    title: 'Undo',
    body: 'Every change goes on an undo stack, and a bulk delete counts as one action. So one undo brings back every document it removed.',
    target: 'undo',
    task: {
      text: 'Press Undo.',
      done: response => called(response, 'store.undo()') && (response.events?.added.length ?? 0) > 1,
      perform: demo => demo.undo(),
    },
  },
  {
    title: 'History',
    body: 'The tour just deleted two documents. This list is the undo stack, newest first. Undo can also reach past the newest change and reverse one document by itself.',
    target: 'panel',
    show: showTab('history'),
    setup: demo => {
      for (const tile of demo.state?.ram.slice(0, 2) ?? []) {
        demo.deleteDocument(tile.name);
      }
    },
    task: {
      text: 'Undo the older delete, which is the lower of the two. The newer one stays in place.',
      done: response => called(response, 'store.undo(uri('),
      perform: demo => {
        const older = demo.state?.history[1] ?? demo.state?.history[0];
        if (older) {
          demo.undoDocument(older.targets[0]);
        }
      },
    },
  },
  {
    title: "That's the tour",
    body: 'Everything here still works. Add a document, tag one or set a tight limit. Press Reset to start over.',
    target: null,
  },
];
