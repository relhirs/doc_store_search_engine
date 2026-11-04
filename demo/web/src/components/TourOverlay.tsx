import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from 'motion/react';
import { useTiming } from '../demo/motion';
import { place, type Box, type Size } from '../demo/placement';
import { STOPS } from '../demo/tour';
import type { Demo, TourPosition } from '../demo/useDemo';

const PADDING = 6;
const MOVE_MS = 220;
// A second press of a double click lands on the next stop's button, which could undo what the first one did
const SETTLE_MS = 350;
// A call that changes nothing never reports back, and the tour must not get stuck waiting for it
const WAIT_MS = 1500;
// Rows take a moment to leave, and the page shrinks when they do, which can carry a feature off screen
const SETTLE_LAYOUT_MS = 500;

/** The part of an element that can be seen, because a long list runs past the panel that scrolls it. */
function visibleBox(element: Element): Box | null {
  const rect = element.getBoundingClientRect();
  let { left, top, right, bottom } = rect;
  for (let parent = element.parentElement; parent; parent = parent.parentElement) {
    if (getComputedStyle(parent).overflowY !== 'visible') {
      const clip = parent.getBoundingClientRect();
      left = Math.max(left, clip.left);
      top = Math.max(top, clip.top);
      right = Math.min(right, clip.right);
      bottom = Math.min(bottom, clip.bottom);
    }
  }
  left = Math.max(left - PADDING, 0);
  top = Math.max(top - PADDING, 0);
  right = Math.min(right + PADDING, window.innerWidth);
  bottom = Math.min(bottom + PADDING, window.innerHeight);
  return right - left > PADDING * 2 && bottom - top > PADDING * 2 ? { left, top, width: right - left, height: bottom - top } : null;
}

const sameBox = (a: Box | null, b: Box | null) =>
  a === b || (!!a && !!b && a.left === b.left && a.top === b.top && a.width === b.width && a.height === b.height);

interface Measured {
  element: Element | null;
  box: Box | null;
  card: Size;
  view: Size;
}

function Spotlight({ demo, tour }: { demo: Demo; tour: TourPosition }) {
  const timing = useTiming();
  const card = useRef<HTMLDivElement>(null);
  const [measured, setMeasured] = useState<Measured>({ element: null, box: null, card: { width: 340, height: 240 }, view: { width: window.innerWidth, height: window.innerHeight } });
  const [waiting, setWaiting] = useState(false);
  const [moving, setMoving] = useState(true);
  const arrived = useRef(0);

  const stop = STOPS[tour.stop];
  const last = tour.stop === STOPS.length - 1;
  const done = !stop.task || tour.done.includes(tour.stop);

  const go = useCallback((next: number) => {
    arrived.current = Date.now();
    setWaiting(false);
    demo.setTour({ ...tour, stop: next, reached: Math.max(tour.reached, next) });
    STOPS[next].show?.(demo);
    if (next > tour.reached) {
      STOPS[next].setup?.(demo);
    }
  }, [demo, tour]);
  const goRef = useRef(go);
  goRef.current = go;

  const forward = () => {
    if (waiting || Date.now() - arrived.current < SETTLE_MS) {
      return;
    }
    if (done) {
      go(tour.stop + 1);
    } else {
      setWaiting(true);
      stop.task?.perform(demo);
    }
  };

  useEffect(() => {
    if (!waiting) {
      return;
    }
    if (done) {
      goRef.current(tour.stop + 1);
      return;
    }
    const timer = window.setTimeout(() => goRef.current(tour.stop + 1), WAIT_MS);
    return () => window.clearTimeout(timer);
  }, [waiting, done, tour.stop]);

  // Features move as results load, tiles slide and panels scroll, so this follows them every frame
  useEffect(() => {
    let frame = 0;
    const settled = performance.now() + SETTLE_LAYOUT_MS;
    const read = () => {
      const element = stop.target ? document.querySelector(`[data-tour="${stop.target}"]`) : null;
      let box = element ? visibleBox(element) : null;
      if (element && element.getBoundingClientRect().height > 0 && (!box || performance.now() < settled)) {
        // A phone scrolls the whole page, and the top leaves the most room for the card below
        element.scrollIntoView({ block: window.innerWidth <= 900 ? 'start' : 'nearest' });
        box = visibleBox(element);
      }
      const size = { width: card.current?.offsetWidth ?? 340, height: card.current?.offsetHeight ?? 240 };
      setMeasured(previous => (
        previous.element === element && sameBox(previous.box, box)
        && previous.card.width === size.width && previous.card.height === size.height
        && previous.view.width === window.innerWidth && previous.view.height === window.innerHeight
      ) ? previous : { element, box, card: size, view: { width: window.innerWidth, height: window.innerHeight } });
      frame = requestAnimationFrame(read);
    };
    read();
    return () => cancelAnimationFrame(frame);
  }, [stop]);

  // The box only glides between stops. While following a feature it has to stay exactly on it
  useEffect(() => {
    setMoving(true);
    const timer = window.setTimeout(() => setMoving(false), MOVE_MS + 40);
    card.current?.focus({ preventScroll: true });
    return () => window.clearTimeout(timer);
  }, [tour.stop]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        demo.setTour(null);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [demo]);

  // The blockers only stop the pointer, so this keeps Tab away from everything under the dim too
  const { element } = measured;
  const rows = demo.rows.length;
  useEffect(() => {
    const app = document.querySelector('.app');
    const closed: HTMLElement[] = [];
    if (app && element) {
      for (let node: Element | null = element; node && node !== app; node = node.parentElement) {
        for (const sibling of node.parentElement?.children ?? []) {
          if (sibling !== node) {
            closed.push(sibling as HTMLElement);
          }
        }
      }
    } else if (app) {
      closed.push(app as HTMLElement);
    }
    closed.forEach(node => { node.inert = true; });
    return () => closed.forEach(node => { node.inert = false; });
  }, [element, rows, demo.state, demo.tab]);

  const { box, view } = measured;
  const hole = box ?? { left: view.width / 2, top: view.height / 2, width: 0, height: 0 };
  const position = place(box, measured.card, view);
  const glide = moving ? timing(MOVE_MS) : { duration: 0 };
  const right = hole.left + hole.width;
  const bottom = hole.top + hole.height;

  return (
    <div className="spotlight">
      <motion.div
        className={box ? 'spotlight__hole' : 'spotlight__hole spotlight__hole--none'}
        initial={false}
        animate={{ x: hole.left, y: hole.top, width: hole.width, height: hole.height }}
        transition={glide}
      />
      <div className="spotlight__block" style={{ left: 0, top: 0, width: '100%', height: hole.top }} />
      <div className="spotlight__block" style={{ left: 0, top: bottom, width: '100%', bottom: 0 }} />
      <div className="spotlight__block" style={{ left: 0, top: hole.top, width: hole.left, height: hole.height }} />
      <div className="spotlight__block" style={{ left: right, top: hole.top, right: 0, height: hole.height }} />
      {position.arrow && (
        <svg className="spotlight__arrow" aria-hidden="true">
          <defs>
            <marker id="spotlight-head" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0 0 L8 4 L0 8 Z" fill="currentColor" />
            </marker>
          </defs>
          <line {...position.arrow} stroke="currentColor" strokeWidth="2" markerEnd="url(#spotlight-head)" />
        </svg>
      )}
      <motion.div
        ref={card}
        className="spotlight__card"
        role="dialog"
        aria-label="Tour"
        aria-live="polite"
        tabIndex={-1}
        initial={false}
        animate={{ x: position.left, y: position.top }}
        transition={glide}
      >
        <p className="spotlight__count">{tour.stop + 1} of {STOPS.length}</p>
        <h2>{stop.title}</h2>
        <p>{stop.body}</p>
        {stop.task && (
          <p className="spotlight__task">{done ? 'Done. Press Next to carry on.' : stop.task.text}</p>
        )}
        <div className="spotlight__actions">
          {!last && <button type="button" className="quiet" onClick={() => demo.setTour(null)}>End tour</button>}
          {tour.stop > 0 && <button type="button" className="quiet" onClick={() => go(tour.stop - 1)}>Back</button>}
          {last
            ? <button type="button" onClick={() => demo.setTour(null)}>Close</button>
            : <button type="button" disabled={waiting} onClick={forward}>{done ? 'Next' : 'Do it for me'}</button>}
        </div>
      </motion.div>
    </div>
  );
}

export function TourOverlay({ demo }: { demo: Demo }) {
  return demo.tour ? <Spotlight demo={demo} tour={demo.tour} /> : null;
}
