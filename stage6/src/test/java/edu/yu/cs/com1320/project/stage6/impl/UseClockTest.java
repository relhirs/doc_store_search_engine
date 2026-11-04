package edu.yu.cs.com1320.project.stage6.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UseClockTest {

    @Test
    void now_NeverRepeats() {
        long previous = UseClock.now();
        for (int i = 0; i < 100000; i++) {
            long next = UseClock.now();
            assertTrue(next > previous, "Each use time must be later than the last");
            previous = next;
        }
    }
}
