import { useReducedMotion } from 'motion/react';

const EASE = [0.2, 0, 0, 1] as const;

/** One curve for everything. With reduced motion, state changes are instant. */
export function useTiming() {
  const reduced = useReducedMotion();
  return (durationMs: number, delayMs = 0) =>
    reduced ? { duration: 0, delay: 0 } : { duration: durationMs / 1000, delay: delayMs / 1000, ease: EASE };
}
