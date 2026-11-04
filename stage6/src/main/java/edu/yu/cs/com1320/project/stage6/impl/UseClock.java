package edu.yu.cs.com1320.project.stage6.impl;

/**
 * Hands out strictly increasing last-use times. A timer alone can return the same value twice, and a
 * tie would leave the choice of which document to evict to chance.
 */
final class UseClock {
    private static long last = Long.MIN_VALUE;

    private UseClock() {
    }

    static synchronized long now() {
        last = Math.max(last + 1, System.nanoTime());
        return last;
    }
}
