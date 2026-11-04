import { useEffect, useRef, useState, type FormEvent } from 'react';
import { motion } from 'motion/react';
import { formatBytes, plural } from '../demo/format';
import { useTiming } from '../demo/motion';
import type { Demo, Tab } from '../demo/useDemo';
import { SOURCES } from './sources';

const TABS: { id: Tab; label: string }[] = [
  { id: 'index', label: 'Index' },
  { id: 'storage', label: 'Storage' },
  { id: 'eviction', label: 'Eviction' },
  { id: 'history', label: 'History' },
];

function Source({ file }: { file: keyof typeof SOURCES }) {
  return <a className="source" href={SOURCES[file].href}>{SOURCES[file].label}</a>;
}

function IndexPanel({ demo }: { demo: Demo }) {
  const timing = useTiming();
  const { levels, query } = demo;
  const typed = query.trim();
  const end = levels[levels.length - 1];
  const stopped = levels.length > 0 && levels.length <= typed.length;
  return (
    <>
      <p className="panel__intro">The word index is a trie with one node per letter. Each row is a node, and its letters are the branches that exist right now.</p>
      <ol className="trie" aria-label="Path through the word index">
        {levels.map((level, depth) => {
          const taken = typed[depth];
          return (
            <li key={level.prefix || 'root'}>
              <code className="trie__prefix">{level.prefix || 'start'}</code>
              <span className="trie__branches">
                {level.next.map(letter => (
                  <motion.span
                    key={letter}
                    className={letter === taken ? 'trie__letter trie__letter--taken' : 'trie__letter'}
                    initial={false}
                    animate={{ opacity: 1 }}
                    transition={timing(40, depth * 40)}
                  >
                    {letter}
                  </motion.span>
                ))}
                {level.next.length === 0 && <span className="trie__end">No longer words</span>}
              </span>
            </li>
          );
        })}
      </ol>
      {typed && end && (
        <p className="panel__note">
          {stopped
            ? <>No word continues with <code>{typed[levels.length - 1]}</code> after <code>{end.prefix || 'the start'}</code>.</>
            : <><code>{end.prefix}</code> is {end.documents > 0 ? `a word in ${plural(end.documents, 'document')}` : 'a prefix, not a word in any document'}.</>}
        </p>
      )}
      <Source file="trie" />
    </>
  );
}

function TagForm({ demo, name }: { demo: Demo; name: string }) {
  const [key, setKey] = useState('');
  const [value, setValue] = useState('');
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (key.trim()) {
      demo.tagDocument(name, key.trim(), value);
      setKey('');
      setValue('');
    }
  };
  return (
    <form className="tag" onSubmit={submit}>
      <input aria-label="Tag name" placeholder="Tag" value={key} onChange={event => setKey(event.target.value)} autoComplete="off" />
      <input aria-label="Tag value" placeholder="Value" value={value} onChange={event => setValue(event.target.value)} autoComplete="off" />
      <button type="submit">Set tag</button>
    </form>
  );
}

function StoragePanel({ demo }: { demo: Demo }) {
  const { state, selected, storedJson } = demo;
  const inRam = state?.ram.find(tile => tile.name === selected);
  const onDisk = state?.disk.find(tile => tile.name === selected);
  const tile = inRam ?? onDisk;
  if (!tile) {
    return (
      <>
        <p className="panel__intro">Documents live in a B-Tree. An entry holds the document while it's in RAM. When a document is evicted, the entry empties and its JSON goes to disk.</p>
        <p className="panel__note">Choose a document on the wall to see where it's stored.</p>
        <div className="sources"><Source file="btree" /><Source file="persistence" /></div>
      </>
    );
  }
  return (
    <>
      <h3 className="doc__name"><code>{tile.name}</code></h3>
      <p className={inRam ? 'doc__tier doc__tier--ram' : 'doc__tier doc__tier--disk'}>
        {inRam ? 'In RAM' : 'On disk'}, {formatBytes(tile.bytes)}
      </p>
      {Object.keys(tile.metadata).length > 0 && (
        <p className="panel__note">{Object.entries(tile.metadata).map(([key, value]) => `${key}: ${value}`).join(', ')}</p>
      )}
      <div className="doc__actions">
        <button type="button" onClick={() => demo.openDocument(tile.name)}>{inRam ? 'Open' : 'Open and bring to RAM'}</button>
        <button type="button" className="danger" onClick={() => demo.deleteDocument(tile.name)}>Delete</button>
      </div>
      {demo.opened?.name === tile.name && inRam && <p className="doc__text">{demo.opened.text}</p>}
      {onDisk && storedJson && (
        <>
          <p className="panel__note">Stored JSON. Reading it here doesn't count as a use.</p>
          <pre className="json"><code>{storedJson}</code></pre>
        </>
      )}
      {inRam && <p className="panel__note">It has no stored JSON until it's evicted.</p>}
      <TagForm demo={demo} name={tile.name} />
      <div className="sources"><Source file="btree" /><Source file="persistence" /></div>
    </>
  );
}

function EvictionPanel({ demo }: { demo: Demo }) {
  const { state } = demo;
  if (!state) {
    return null;
  }
  const others = state.ram.filter(tile => tile.name !== state.nextToEvict);
  const next = state.ram.find(tile => tile.name === state.nextToEvict);
  const queue = next ? [next, ...others] : others;
  return (
    <>
      <p className="panel__intro">A min-heap ordered by last use picks what leaves RAM next. Documents returned by one search share a last use, so among those, the heap decides.</p>
      <p className="panel__note">
        {state.ramCount} of {state.maxDocuments} slots used, {formatBytes(state.ramBytes)} in RAM
      </p>
      <ol className="queue">
        {queue.map((tile, i) => (
          <li key={tile.name}>
            <button type="button" className="queue__row" onClick={() => demo.selectTile(tile.name)}>
              <code>{tile.name}</code>
              <span>{i === 0 ? 'Next to leave' : formatBytes(tile.bytes)}</span>
            </button>
          </li>
        ))}
      </ol>
      {state.disk.length > 0 && (
        <>
          <h3 className="panel__heading">On disk</h3>
          <ol className="queue queue--disk">
            {state.disk.map(tile => (
              <li key={tile.name}>
                <button type="button" className="queue__row" onClick={() => demo.selectTile(tile.name)}>
                  <code>{tile.name}</code>
                  <span>{formatBytes(tile.bytes)}</span>
                </button>
              </li>
            ))}
          </ol>
        </>
      )}
      <Source file="heap" />
    </>
  );
}

function HistoryPanel({ demo }: { demo: Demo }) {
  const timing = useTiming();
  const history = demo.state?.history ?? [];
  // store.undo(uri) reverses the latest action on a document, so only that entry can offer it
  const seen = new Set<string>();
  const entries = history.map(entry => {
    const undoable = entry.targets.filter(target => !seen.has(target));
    undoable.forEach(target => seen.add(target));
    return { entry, undoable };
  });
  return (
    <>
      <p className="panel__intro">Every change pushes a command on a stack. A bulk delete is one command, and any document can be undone by itself.</p>
      {entries.length === 0 && <p className="panel__note">Nothing to undo yet. Delete or add a document and it appears here.</p>}
      <ol className="history">
        {entries.map(({ entry, undoable }, i) => (
          <motion.li key={`${history.length - i}:${entry.label}`} layout="position" transition={timing(220)}>
            <code>{entry.label}</code>
            {entry.bulk && <span className="history__bulk">One action, {plural(entry.targets.length, 'document')}</span>}
            <div className="history__targets">
              {undoable.map(target => (
                <button key={target} type="button" className="chip" onClick={() => demo.undoDocument(target)}>
                  Undo {target}
                </button>
              ))}
            </div>
          </motion.li>
        ))}
      </ol>
      <Source file="stack" />
    </>
  );
}

export function EnginePanels({ demo }: { demo: Demo }) {
  const { tab, engineOpen, selected } = demo;
  const section = useRef<HTMLElement>(null);

  // On a phone this section sits below the wall, so a tapped tile would otherwise open out of sight
  useEffect(() => {
    if (selected && window.matchMedia('(max-width: 900px)').matches) {
      section.current?.scrollIntoView({ block: 'start' });
    }
  }, [selected]);

  return (
    <section ref={section} className={engineOpen ? 'engine' : 'engine engine--closed'} aria-label="Inside the engine">
      <button type="button" className="engine__toggle" aria-expanded={engineOpen} onClick={() => demo.setEngineOpen(!engineOpen)}>
        Inside the engine
      </button>
      <div className="tabs" role="tablist" aria-label="Engine views">
        {TABS.map(({ id, label }) => (
          <button key={id} type="button" role="tab" id={`tab-${id}`} aria-selected={tab === id} aria-controls="engine-panel" onClick={() => demo.setTab(id)}>
            {label}
          </button>
        ))}
      </div>
      <div className="panel" id="engine-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {tab === 'index' && <IndexPanel demo={demo} />}
        {tab === 'storage' && <StoragePanel demo={demo} />}
        {tab === 'eviction' && <EvictionPanel demo={demo} />}
        {tab === 'history' && <HistoryPanel demo={demo} />}
      </div>
    </section>
  );
}
