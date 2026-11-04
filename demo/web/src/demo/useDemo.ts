import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CORPUS } from '../corpus';
import { EngineClient } from '../engine/client';
import type { EngineState, Events, IndexLevel, Response, Row } from '../engine/types';
import { describeCall } from './format';
import { TOUR } from './tour';

export const SEED_LIMIT = 16;
const SEARCH_PAUSE_MS = 150;
const SWEEP_MS = 800;

export type Mode = 'prefix' | 'exact';
export type Tab = 'index' | 'storage' | 'eviction' | 'history';

export interface Filter {
  key: string;
  value: string;
}

export interface LastCall {
  call: string;
  summary: string;
  nanos: number | null;
  ok: boolean;
}

export interface TourPosition {
  step: number;
  task: number;
}

const NO_EVENTS: Events = { evicted: [], loaded: [], added: [], deleted: [] };

function nextDiskOrder(previous: string[], response: Response): string[] {
  const onDisk = new Set(response.state.disk.map(tile => tile.name));
  const evicted = [...(response.events?.evicted ?? [])].reverse();
  const kept = previous.filter(name => onDisk.has(name) && !evicted.includes(name));
  const known = new Set([...evicted, ...kept]);
  return [...evicted, ...kept, ...response.state.disk.map(tile => tile.name).filter(name => !known.has(name))];
}

function reachesWord(levels: IndexLevel[], text: string, mode: Mode): boolean {
  const end = levels[levels.length - 1];
  return levels.length === text.length + 1 && (end.documents > 0 || (mode === 'prefix' && end.next.length > 0));
}

export function useDemo() {
  const client = useRef<EngineClient | null>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [state, setState] = useState<EngineState | null>(null);
  const [last, setLast] = useState<LastCall | null>(null);
  const [events, setEvents] = useState<Events>(NO_EVENTS);
  const [indexRevision, setIndexRevision] = useState(0);
  const [diskOrder, setDiskOrder] = useState<string[]>([]);
  const [generation, setGeneration] = useState(0);
  const [sweeping, setSweeping] = useState(false);

  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<Mode>('prefix');
  const [filters, setFilters] = useState<Filter[]>([]);
  const [rows, setRows] = useState<Row[]>([]);
  const [searched, setSearched] = useState(false);
  const [suggestion, setSuggestion] = useState<{ text: string; mode: Mode } | null>(null);
  const [levels, setLevels] = useState<IndexLevel[]>([]);

  const [tab, setTab] = useState<Tab>('index');
  const [engineOpen, setEngineOpen] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [opened, setOpened] = useState<Row | null>(null);
  const [storedJson, setStoredJson] = useState<string | null>(null);
  const [tour, setTour] = useState<TourPosition | null>(null);
  const [tourOffered, setTourOffered] = useState(true);
  const tourRef = useRef(tour);
  tourRef.current = tour;

  const text = query.trim();
  const filterLines = useMemo(() => filters.map(({ key, value }) => `${key}\t${value}`).join('\n'), [filters]);

  const advanceTour = useCallback((response: Response) => {
    const position = tourRef.current;
    if (!position || position.step >= TOUR.length || !TOUR[position.step].tasks[position.task].done(response)) {
      return;
    }
    const step = TOUR[position.step];
    if (position.task + 1 < step.tasks.length) {
      setTour({ step: position.step, task: position.task + 1 });
    } else {
      setTour({ step: position.step + 1, task: 0 });
    }
  }, []);

  const apply = useCallback((response: Response) => {
    setState(response.state);
    setDiskOrder(previous => nextDiskOrder(previous, response));
    const changes = response.events ?? NO_EVENTS;
    setEvents(changes);
    if (changes.added.length || changes.deleted.length) {
      setIndexRevision(revision => revision + 1);
    }
    if (response.call) {
      setLast({
        call: response.call,
        summary: response.ok ? describeCall(response) : response.error ?? '',
        nanos: response.storeNanos ?? null,
        ok: response.ok,
      });
      advanceTour(response);
    } else if (!response.ok) {
      setLast({ call: 'Not sent to the store', summary: response.error ?? '', nanos: null, ok: false });
    }
  }, [advanceTour]);

  const load = useCallback(async () => {
    const engine = client.current!;
    setQuery('');
    setFilters([]);
    setRows([]);
    setSearched(false);
    setSelected(null);
    setOpened(null);
    setTab('index');
    await engine.call('reset');
    const puts = CORPUS.map(article => engine.call('putDocument', article.name, article.text));
    for (const article of CORPUS) {
      engine.call('tagDocument', article.name, 'topic', article.topic);
      engine.call('tagDocument', article.name, 'era', article.era);
    }
    const limit = await engine.call('setMaxDocuments', SEED_LIMIT);
    const loaded = await engine.call('finishLoading');
    const nanos = (await Promise.all(puts)).reduce((total, put) => total + (put.storeNanos ?? 0), 0);
    setDiskOrder(limit.events ? [...limit.events.evicted].reverse() : []);
    setEvents(NO_EVENTS);
    setState(loaded.state);
    setIndexRevision(revision => revision + 1);
    setGeneration(value => value + 1);
    setLast({ call: `store.put(text, uri(…), TXT), ${CORPUS.length} times`, summary: `${CORPUS.length} documents loaded`, nanos, ok: true });
    setSweeping(true);
    window.setTimeout(() => setSweeping(false), SWEEP_MS);
  }, []);

  useEffect(() => {
    // StrictMode runs effects twice in development, and the corpus must only load once
    if (client.current) {
      return;
    }
    const engine = new EngineClient();
    client.current = engine;
    engine.ready.then(load).then(() => setStatus('ready')).catch(() => setStatus('failed'));
  }, [load]);

  useEffect(() => {
    if (status !== 'ready') {
      return;
    }
    if (!text && !filterLines) {
      setRows([]);
      setSearched(false);
      setSuggestion(null);
      return;
    }
    const timer = window.setTimeout(async () => {
      const op = mode === 'prefix' ? 'searchPrefix' : 'search';
      const response = await client.current!.latest<Row[]>('search', op, text, filterLines);
      if (response) {
        apply(response);
        setRows(response.result ?? []);
        setSearched(true);
      }
    }, SEARCH_PAUSE_MS);
    return () => window.clearTimeout(timer);
  }, [text, mode, filterLines, status, apply]);

  // The index is read-only, so this can follow every keystroke without counting as a use
  useEffect(() => {
    if (status !== 'ready') {
      return;
    }
    client.current!.latest<IndexLevel[]>('index', 'indexView', text).then(response => {
      if (response) {
        setLevels(response.result ?? []);
      }
    });
  }, [text, indexRevision, status]);

  // Search is case sensitive, so an empty result often has a near neighbour. Checked in the read-only index
  useEffect(() => {
    if (!searched || rows.length > 0 || !text) {
      setSuggestion(null);
      return;
    }
    let current = true;
    (async () => {
      const variants = [text.toLowerCase(), text[0].toUpperCase() + text.slice(1).toLowerCase(), text.toUpperCase()];
      for (const variant of new Set([text, ...variants])) {
        const view = await client.current!.call<IndexLevel[]>('indexView', variant);
        const found = view.result ?? [];
        if (variant !== text && reachesWord(found, variant, mode) && current) {
          setSuggestion({ text: variant, mode });
          return;
        }
        if (variant === text && mode === 'exact' && reachesWord(found, variant, 'prefix') && current) {
          setSuggestion({ text: variant, mode: 'prefix' });
          return;
        }
      }
      if (current) {
        setSuggestion(null);
      }
    })();
    return () => {
      current = false;
    };
  }, [searched, rows, text, mode]);

  const onDisk = selected !== null && !!state?.disk.some(tile => tile.name === selected);
  useEffect(() => {
    if (!selected || !onDisk) {
      setStoredJson(null);
      return;
    }
    client.current!.call<string>('diskJson', selected).then(response => setStoredJson(response.result ?? null));
  }, [selected, onDisk]);

  const run = useCallback(async <Result,>(op: string, ...args: unknown[]) => {
    const response = await client.current!.call<Result>(op, ...args);
    apply(response);
    return response;
  }, [apply]);

  const setLimit = useCallback((limit: number) => {
    client.current!.latest('limit', 'setMaxDocuments', limit).then(response => {
      if (response) {
        apply(response);
      }
    });
  }, [apply]);

  const openDocument = useCallback(async (name: string) => {
    const response = await run<Row | null>('getDocument', name);
    setOpened(response.ok ? response.result ?? null : null);
    setSelected(name);
  }, [run]);

  const selectTile = useCallback((name: string) => {
    setSelected(name);
    setTab('storage');
    setEngineOpen(true);
  }, []);

  const deleteDocument = useCallback(async (name: string) => {
    await run('deleteDocument', name);
    setOpened(current => (current?.name === name ? null : current));
  }, [run]);

  const deleteResults = useCallback(() => {
    return run(mode === 'prefix' ? 'deleteAllPrefix' : 'deleteAll', text, filterLines);
  }, [run, mode, text, filterLines]);

  const addDocument = useCallback(async (name: string, text: string, topic: string) => {
    const response = await run('putDocument', name, text);
    if (response.ok && topic) {
      await run('tagDocument', name, 'topic', topic);
    }
    return response;
  }, [run]);

  const exists = useCallback((name: string) => {
    return !!state && (state.ram.some(tile => tile.name === name) || state.disk.some(tile => tile.name === name));
  }, [state]);

  return {
    status, state, last, events, diskOrder, generation, sweeping,
    query, setQuery, mode, setMode, filters, setFilters, searched, suggestion, levels,
    // A deleted row comes back with its document on undo, with no new search needed
    rows: rows.filter(row => exists(row.name)),
    tab, setTab, engineOpen, setEngineOpen, selected, setSelected, opened, setOpened, storedJson,
    tour, setTour, tourOffered, setTourOffered,
    reset: load, setLimit, openDocument, selectTile, deleteDocument, deleteResults, addDocument,
    tagDocument: (name: string, key: string, value: string) => run('tagDocument', name, key, value),
    undo: () => run('undoLast'),
    undoDocument: (name: string) => run('undoDocument', name),
  };
}

export type Demo = ReturnType<typeof useDemo>;
