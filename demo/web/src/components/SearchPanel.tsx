import { useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { CORPUS } from '../corpus';
import { escapeRegExp, plural, snippet } from '../demo/format';
import { useTiming } from '../demo/motion';
import type { Demo, Mode } from '../demo/useDemo';
import type { Row } from '../engine/types';
import { AddDocument } from './AddDocument';
import { Tour } from './Tour';

const titleOf = (name: string) => CORPUS.find(article => article.name === name)?.title ?? name;

function Highlighted({ text, query, mode }: { text: string; query: string; mode: Mode }) {
  if (!query.trim()) {
    return <>{text}</>;
  }
  const word = escapeRegExp(query.trim());
  const pattern = new RegExp(mode === 'prefix' ? `(\\b${word}[a-zA-Z0-9]*)` : `(\\b${word}\\b)`, 'g');
  return <>{text.split(pattern).map((part, i) => (i % 2 ? <mark key={i}>{part}</mark> : part))}</>;
}

function Filters({ demo }: { demo: Demo }) {
  const [adding, setAdding] = useState(false);
  const { filters, state } = demo;
  const options = useMemo(() => {
    const found = new Set<string>();
    for (const tile of [...(state?.ram ?? []), ...(state?.disk ?? [])]) {
      for (const [key, value] of Object.entries(tile.metadata)) {
        found.add(`${key}: ${value}`);
      }
    }
    return [...found].sort().filter(option => !filters.some(({ key, value }) => `${key}: ${value}` === option));
  }, [state, filters]);

  return (
    <div className="filters">
      {filters.map(filter => (
        <button
          key={filter.key}
          type="button"
          className="chip"
          aria-label={`Remove filter ${filter.key}: ${filter.value}`}
          onClick={() => demo.setFilters(filters.filter(other => other !== filter))}
        >
          {filter.key}: {filter.value} <span aria-hidden="true">×</span>
        </button>
      ))}
      {adding ? (
        <select
          autoFocus
          aria-label="Choose a filter"
          value=""
          onBlur={() => setAdding(false)}
          onChange={event => {
            const [key, value] = event.target.value.split(': ');
            demo.setFilters([...filters.filter(filter => filter.key !== key), { key, value }]);
            setAdding(false);
          }}
        >
          <option value="" disabled>Choose a filter</option>
          {options.map(option => <option key={option}>{option}</option>)}
        </select>
      ) : (
        <button type="button" className="quiet" onClick={() => setAdding(true)}>Add filter</button>
      )}
    </div>
  );
}

function Result({ row, top, demo }: { row: Row; top: number; demo: Demo }) {
  const timing = useTiming();
  const inRam = demo.state?.ram.some(tile => tile.name === row.name);
  const isOpen = demo.opened?.name === row.name;
  const share = row.count !== undefined && top > 0 ? (row.count / top) * 100 : 0;
  return (
    <motion.li layout="position" transition={timing(220)} exit={{ opacity: 0, transition: timing(180) }}>
      <button
        type="button"
        className="result"
        aria-expanded={isOpen}
        onClick={() => (isOpen ? demo.setOpened(null) : demo.openDocument(row.name))}
      >
        <span className="result__title">{titleOf(row.name)}</span>
        {row.count !== undefined && <span className="result__count">{plural(row.count, 'hit')}</span>}
        {row.count !== undefined && (
          <span className="result__track" aria-hidden="true">
            <motion.span
              className={inRam ? 'result__bar result__bar--ram' : 'result__bar result__bar--disk'}
              initial={false}
              animate={{ width: `${share}%` }}
              transition={timing(220)}
            />
          </span>
        )}
        {!isOpen && <span className="result__snippet">{snippet(row, demo.query.trim())}</span>}
      </button>
      {isOpen && (
        <div className="reader">
          <p><Highlighted text={demo.opened!.text} query={demo.query} mode={demo.mode} /></p>
          <p className="reader__meta">
            {Object.entries(row.metadata).map(([key, value]) => `${key}: ${value}`).join(', ')}
          </p>
          <button type="button" className="danger" onClick={() => demo.deleteDocument(row.name)}>Delete document</button>
        </div>
      )}
    </motion.li>
  );
}

function EmptyState({ demo }: { demo: Demo }) {
  const { query, suggestion, searched } = demo;
  if (!searched) {
    return (
      <p className="empty">
        Search {plural((demo.state?.ramCount ?? 0) + (demo.state?.disk.length ?? 0), 'document')}. Try{' '}
        {['tr', 'disk', 'heap'].map((word, i) => (
          <span key={word}>
            {i > 0 && ', '}
            <button type="button" className="inline" onClick={() => demo.setQuery(word)}>{word}</button>
          </span>
        ))}.
      </p>
    );
  }
  return (
    <p className="empty">
      Nothing matches {query.trim() ? <code>{query.trim()}</code> : 'those filters'}.{' '}
      {suggestion ? (
        <>
          Search is case sensitive.{' '}
          <button
            type="button"
            className="inline"
            onClick={() => { demo.setQuery(suggestion.text); demo.setMode(suggestion.mode); }}
          >
            Try {suggestion.mode === 'prefix' && demo.mode === 'exact' ? 'prefix search' : suggestion.text}
          </button>
        </>
      ) : 'Search is case sensitive, and only ASCII letters and digits are indexed.'}
    </p>
  );
}

export function SearchPanel({ demo }: { demo: Demo }) {
  const { query, mode, rows } = demo;
  const top = Math.max(0, ...rows.map(row => row.count ?? 0));
  return (
    <section className="search" aria-label="Search">
      <Tour demo={demo} />
      <div className="search__bar">
        <input
          type="search"
          value={query}
          placeholder="Search the documents"
          aria-label="Search the documents"
          autoComplete="off"
          spellCheck={false}
          onChange={event => demo.setQuery(event.target.value)}
        />
        <div className="segmented" role="group" aria-label="Search type">
          {(['exact', 'prefix'] as Mode[]).map(option => (
            <button key={option} type="button" aria-pressed={mode === option} onClick={() => demo.setMode(option)}>
              {option === 'exact' ? 'Exact' : 'Prefix'}
            </button>
          ))}
        </div>
      </div>
      <Filters demo={demo} />
      {rows.length > 0 ? (
        <>
          <div className="results__head">
            <span>{plural(rows.length, 'result')}</span>
            <button type="button" className="danger" onClick={() => demo.deleteResults()}>Delete all {rows.length}</button>
          </div>
          <ol className="results">
            <AnimatePresence initial={false}>
              {rows.map(row => <Result key={row.name} row={row} top={top} demo={demo} />)}
            </AnimatePresence>
          </ol>
        </>
      ) : <EmptyState demo={demo} />}
      <AddDocument demo={demo} />
    </section>
  );
}
