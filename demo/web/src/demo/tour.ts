import type { Response } from '../engine/types';

export interface TourTask {
  text: string;
  done: (response: Response) => boolean;
}

export interface TourStep {
  title: string;
  tasks: TourTask[];
}

const called = (response: Response, start: string) => response.ok && !!response.call?.startsWith(start);

/** Every task waits for a real call to the engine, so the tour can't move on by itself. */
export const TOUR: TourStep[] = [
  {
    title: 'Search',
    tasks: [
      {
        text: 'Type tr in the search box. The index walks one letter at a time, and documents rank by how many matching words they hold.',
        done: response => called(response, 'store.searchByPrefix("tr")'),
      },
    ],
  },
  {
    title: 'Filter',
    tasks: [
      {
        text: 'Add the filter topic: trees. Keyword and metadata search now run as one call.',
        done: response => called(response, 'store.searchByPrefixAndMetadata("tr", Map.of("topic", "trees"))'),
      },
    ],
  },
  {
    title: 'Limit memory',
    tasks: [
      {
        text: 'Drag the divider to the left. Each slot you cross pushes the least recently used document out to disk.',
        done: response => called(response, 'store.setMaxDocumentCount') && !!response.events?.evicted.length,
      },
      {
        text: 'Pick a document on the disk side and open it. It comes back to RAM, and another one has to leave.',
        done: response => called(response, 'store.get') && !!response.events?.loaded.length,
      },
    ],
  },
  {
    title: 'Undo',
    tasks: [
      {
        text: 'Delete all the search results at once, then press Undo. A bulk delete is one action, so one undo restores every document.',
        done: response => called(response, 'store.undo()') && !!response.events?.added.length && response.events.added.length > 1,
      },
      {
        text: 'Delete two documents, then open History and undo the older one. The newer delete stays in place.',
        done: response => called(response, 'store.undo(uri(') && response.state.history.length > 0,
      },
    ],
  },
];
