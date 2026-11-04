import { useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { formatBytes, plural } from '../demo/format';
import { useTiming } from '../demo/motion';
import type { Demo } from '../demo/useDemo';
import type { EngineState, Tile } from '../engine/types';

const RAM_BLUE = '#1E4FD6';
const DISK_OCHRE = '#8F5E00';
const SHEET = '#E8EDF1';
const SIGNAL_RED = '#BE2F1C';

const NARROW = 640;
const HANDLE = 28;
const DISK_MIN = 120;
const SHELF_HEIGHT = 150;
const MIN_SPINE = 124;
const GAP = 3;
const COLUMNS = 8;
const ROW = 116;
const STEPPER_ROW = 58;

interface Placed {
  tile: Tile;
  tier: 'ram' | 'disk';
  x: number;
  y: number;
  width: number;
  height: number;
}

interface Layout {
  placed: Placed[];
  empty: { x: number; y: number; width: number; height: number }[];
  height: number;
  pitch: number;
  divider: number;
  slots: number;
}

/**
 * RAM runs from the most recent on the left to the next to leave beside the divider, so evictions have
 * the shortest way to go. Tiles sit left, so raising the limit opens empty slots and moves nothing.
 */
function ramOrder(state: EngineState): Tile[] {
  const recentFirst = [...state.ram].reverse();
  const next = recentFirst.find(tile => tile.name === state.nextToEvict);
  return next ? [...recentFirst.filter(tile => tile !== next), next] : recentFirst;
}

function diskTiles(state: EngineState, order: string[]): Tile[] {
  const rank = new Map(order.map((name, i) => [name, i]));
  return [...state.disk].sort((a, b) => (rank.get(a.name) ?? order.length) - (rank.get(b.name) ?? order.length));
}

function spineHeight(bytes: number, smallest: number, largest: number): number {
  const share = largest > smallest ? (bytes - smallest) / (largest - smallest) : 1;
  return MIN_SPINE + share * (SHELF_HEIGHT - MIN_SPINE);
}

function shelf(width: number, state: EngineState, order: string[]): Layout {
  const ram = ramOrder(state);
  const disk = diskTiles(state, order);
  const all = [...ram, ...disk];
  const limit = state.maxDocuments;
  const slots = Math.max(24, all.length, limit);
  const pitch = (width - DISK_MIN - HANDLE) / slots;
  const tileWidth = Math.max(pitch - GAP, 8);
  const smallest = Math.min(...all.map(tile => tile.bytes));
  const largest = Math.max(...all.map(tile => tile.bytes));
  const divider = limit * pitch;
  const diskStart = divider + HANDLE;
  const diskPitch = disk.length > 1 ? Math.min(pitch, (width - diskStart - tileWidth) / (disk.length - 1)) : pitch;
  const place = (tile: Tile, tier: Placed['tier'], x: number): Placed => {
    const height = spineHeight(tile.bytes, smallest, largest);
    return { tile, tier, x, y: SHELF_HEIGHT - height, width: tileWidth, height };
  };
  return {
    placed: [
      ...ram.map((tile, i) => place(tile, 'ram', i * pitch)),
      ...disk.map((tile, i) => place(tile, 'disk', diskStart + i * diskPitch)),
    ],
    empty: Array.from({ length: Math.max(limit - ram.length, 0) }, (_, i) => ({
      x: (ram.length + i) * pitch, y: SHELF_HEIGHT - MIN_SPINE, width: tileWidth, height: MIN_SPINE,
    })),
    height: SHELF_HEIGHT,
    pitch,
    divider,
    slots,
  };
}

function stacked(width: number, state: EngineState, order: string[]): Layout {
  const ram = ramOrder(state);
  const disk = diskTiles(state, order);
  const pitch = width / COLUMNS;
  const tileWidth = pitch - GAP;
  const limit = state.maxDocuments;
  const ramRows = Math.ceil(Math.max(limit, ram.length) / COLUMNS);
  const divider = ramRows * ROW;
  const diskTop = divider + STEPPER_ROW;
  const cell = (i: number, top: number) => ({ x: (i % COLUMNS) * pitch, y: top + Math.floor(i / COLUMNS) * ROW, width: tileWidth, height: ROW - GAP });
  return {
    placed: [
      ...ram.map((tile, i) => ({ tile, tier: 'ram' as const, ...cell(i, 0) })),
      ...disk.map((tile, i) => ({ tile, tier: 'disk' as const, ...cell(i, diskTop) })),
    ],
    empty: Array.from({ length: Math.max(limit - ram.length, 0) }, (_, i) => cell(ram.length + i, 0)),
    height: diskTop + Math.max(Math.ceil(disk.length / COLUMNS), 1) * ROW,
    pitch,
    divider,
    slots: Math.max(24, ram.length + disk.length, limit),
  };
}

function Spine({ placed, index, demo, delayed }: { placed: Placed; index: number; demo: Demo; delayed: { delay: number; duration: number } }) {
  const timing = useTiming();
  const { tile, tier, x, y, width, height } = placed;
  const color = tier === 'ram' ? RAM_BLUE : DISK_OCHRE;
  const isNext = demo.state?.nextToEvict === tile.name;
  const classes = ['spine', isNext ? 'spine--next' : '', demo.selected === tile.name ? 'spine--selected' : ''].join(' ');
  return (
    <motion.button
      type="button"
      className={classes}
      style={{ width, height, originY: 1, zIndex: index }}
      title={`${tile.name}, ${formatBytes(tile.bytes)}`}
      aria-label={`${tile.name}, ${tier === 'ram' ? 'in RAM' : 'on disk'}${isNext ? ', next to leave' : ''}`}
      initial={demo.sweeping ? { x, y: y - 14, opacity: 0, backgroundColor: color } : { x, y, scaleY: 0, backgroundColor: color }}
      animate={{ x, y, opacity: 1, scaleY: 1, backgroundColor: color }}
      exit={{ scaleY: 0, backgroundColor: SHEET, boxShadow: `inset 0 0 0 2px ${SIGNAL_RED}`, transition: timing(180) }}
      transition={demo.sweeping ? timing(220, index * 20) : timing(delayed.duration, delayed.delay)}
      onClick={() => demo.selectTile(tile.name)}
    >
      <span>{tile.name}</span>
    </motion.button>
  );
}

export function Wall({ demo }: { demo: Demo }) {
  const { state, diskOrder, events, generation } = demo;
  const field = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [dragX, setDragX] = useState<number | null>(null);
  const [dragged, setDragged] = useState(false);

  useLayoutEffect(() => {
    const element = field.current;
    if (!element) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const narrow = width > 0 && width < NARROW;
  const layout = useMemo(() => {
    if (!state || width === 0) {
      return null;
    }
    return narrow ? stacked(width, state, diskOrder) : shelf(width, state, diskOrder);
  }, [state, width, narrow, diskOrder]);

  const moves = useMemo(() => {
    const planned = new Map<string, { delay: number; duration: number }>();
    events.evicted.forEach((name, i) => planned.set(name, { delay: i * 60, duration: 360 }));
    events.loaded.forEach(name => planned.set(name, { delay: 0, duration: 360 }));
    return planned;
  }, [events]);

  const limit = state?.maxDocuments ?? 0;
  const setLimit = (next: number) => {
    const clamped = Math.min(Math.max(next, 1), layout?.slots ?? next);
    if (clamped !== limit) {
      demo.setLimit(clamped);
    }
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragged(true);
    setDragX(layout?.divider ?? 0);
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (dragX === null || !layout || !field.current) {
      return;
    }
    const left = field.current.getBoundingClientRect().left;
    const x = Math.min(Math.max(event.clientX - left - HANDLE / 2, layout.pitch), layout.slots * layout.pitch);
    setDragX(x);
    setLimit(Math.round(x / layout.pitch));
  };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const moves: Record<string, number> = { ArrowLeft: limit - 1, ArrowDown: limit - 1, ArrowRight: limit + 1, ArrowUp: limit + 1, Home: 1, End: layout?.slots ?? limit };
    if (event.key in moves) {
      event.preventDefault();
      setDragged(true);
      setLimit(moves[event.key]);
    }
  };

  const handleX = dragX ?? layout?.divider ?? 0;
  return (
    <section className="wall" aria-label="Memory">
      <div className="wall__labels">
        <p className="wall__ram">
          <strong>RAM</strong> {state ? `${state.ramCount} of ${state.maxDocuments} slots, ${formatBytes(state.ramBytes)}` : ''}
        </p>
        <p className="wall__disk">
          <strong>Disk</strong> {state ? plural(state.disk.length, 'document') : ''}, kept as JSON in browser memory here. On a JVM these are real files.
        </p>
      </div>
      <div className="wall__field" ref={field} style={{ height: layout?.height ?? SHELF_HEIGHT }}>
        {layout?.empty.map((slot, i) => (
          <span key={i} className="slot" style={{ transform: `translate(${slot.x}px, ${slot.y}px)`, width: slot.width, height: slot.height }} />
        ))}
        {layout && (
          <AnimatePresence key={generation}>
            {layout.placed.map((placed, i) => (
              <Spine
                key={placed.tile.name}
                placed={placed}
                index={i}
                demo={demo}
                delayed={moves.get(placed.tile.name) ?? { delay: 0, duration: 220 }}
              />
            ))}
          </AnimatePresence>
        )}
        {layout && !narrow && (
          <div
            className="handle"
            role="slider"
            tabIndex={0}
            aria-label="Memory limit in documents"
            aria-valuemin={1}
            aria-valuemax={layout.slots}
            aria-valuenow={limit}
            style={{ transform: `translateX(${handleX}px)` }}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={() => setDragX(null)}
            onPointerCancel={() => setDragX(null)}
            onKeyDown={onKeyDown}
          >
            <span />
          </div>
        )}
        {layout && narrow && (
          <div className="stepper" style={{ transform: `translateY(${layout.divider}px)`, height: STEPPER_ROW }}>
            <button type="button" aria-label="Lower the memory limit" onClick={() => setLimit(limit - 1)} disabled={limit <= 1}>−</button>
            <span>Memory limit: {plural(limit, 'document')}</span>
            <button type="button" aria-label="Raise the memory limit" onClick={() => setLimit(limit + 1)} disabled={limit >= layout.slots}>+</button>
          </div>
        )}
      </div>
      {layout && !narrow && !dragged && (
        <p className="wall__hint" style={{ left: Math.max(handleX + HANDLE / 2, 0) }}>Drag to change the memory limit</p>
      )}
    </section>
  );
}
