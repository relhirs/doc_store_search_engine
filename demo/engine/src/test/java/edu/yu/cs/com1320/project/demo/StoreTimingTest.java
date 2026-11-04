package edu.yu.cs.com1320.project.demo;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.yu.cs.com1320.project.demo.Responses.*;
import static org.junit.jupiter.api.Assertions.*;

public class StoreTimingTest {

    private static final long TICK = 1000;

    private long reads;
    private Engine engine;

    @BeforeEach
    void setUp() {
        this.reads = 0;
        this.engine = new Engine(() -> ++this.reads * TICK);
        this.engine.putDocument("a", "tree trie shared");
        this.engine.putDocument("b", "tree shared");
        this.engine.tagDocument("a", "topic", "trees");
    }

    private void assertTimedOnce(String label, Supplier<String> call) {
        long before = this.reads;
        JsonObject response = parse(call.get());
        assertEquals(2, this.reads - before, label + " should read the clock around one store call");
        assertEquals(TICK, response.get("storeNanos").getAsLong(), label);
    }

    private void assertNotTimed(String label, String response) {
        assertTrue(parse(response).get("storeNanos").isJsonNull(), label + " never reached the store");
    }

    @Test
    void everyStoreCall_IsTimedOnce() {
        assertTimedOnce("put", () -> this.engine.putDocument("c", "tree"));
        assertTimedOnce("tag", () -> this.engine.tagDocument("c", "topic", "trees"));
        assertTimedOnce("get", () -> this.engine.getDocument("c"));
        assertTimedOnce("search", () -> this.engine.search("tree", ""));
        assertTimedOnce("search with filter", () -> this.engine.search("tree", "topic\ttrees"));
        assertTimedOnce("prefix", () -> this.engine.searchPrefix("tr", ""));
        assertTimedOnce("prefix with filter", () -> this.engine.searchPrefix("tr", "topic\ttrees"));
        assertTimedOnce("filter alone", () -> this.engine.search("", "topic\ttrees"));
        assertTimedOnce("document limit", () -> this.engine.setMaxDocuments(2));
        assertTimedOnce("byte limit", () -> this.engine.setMaxBytes(500));
        assertTimedOnce("delete", () -> this.engine.deleteDocument("c"));
        assertTimedOnce("undo", () -> this.engine.undoLast());
        assertTimedOnce("delete again", () -> this.engine.deleteDocument("c"));
        assertTimedOnce("undo one document", () -> this.engine.undoDocument("c"));
        assertTimedOnce("delete by word", () -> this.engine.deleteAll("trie", ""));
        assertTimedOnce("delete by word and filter", () -> this.engine.deleteAll("shared", "topic\tnone"));
        assertTimedOnce("delete by prefix", () -> this.engine.deleteAllPrefix("sha", ""));
        assertTimedOnce("delete by prefix and filter", () -> this.engine.deleteAllPrefix("tr", "topic\tnone"));
        assertTimedOnce("delete by filter", () -> this.engine.deleteAll("", "topic\tnone"));
    }

    @Test
    void theBridgesOwnWork_StaysOutsideTheTimer() {
        long[] diskCallsAtStop = {-1};
        Engine[] watched = new Engine[1];
        long[] clockReads = {0};
        watched[0] = new Engine(() -> {
            if (++clockReads[0] % 2 == 0) {
                diskCallsAtStop[0] = watched[0].disk().calls().size();
            }
            return clockReads[0];
        });
        for (String name : List.of("a", "b", "c")) {
            watched[0].putDocument(name, "shared");
        }
        watched[0].setMaxDocuments(1);
        assertEquals(2, diskCallsAtStop[0], "the timer has to stop after the evictions the store did");
    }

    /** A clock can't see what ran between its two reads, so this holds the timers to the source itself. */
    @Test
    void everyTimer_WrapsNothingButAStoreCall() throws IOException {
        String source = Files.readString(Path.of("src/main/java/edu/yu/cs/com1320/project/demo/Engine.java"));
        Matcher timers = Pattern.compile("timed\\(\\(\\) -> (\\{\\s*)?(\\S+)").matcher(source);
        int count = 0;
        while (timers.find()) {
            assertTrue(timers.group(2).startsWith("this.store."), "timer around " + timers.group(2));
            count++;
        }
        assertEquals(18, count);
    }

    @Test
    void callsTheBridgeRejects_NeverReachTheStore() {
        assertNotTimed("bad name", this.engine.putDocument("Not Valid", "text"));
        assertNotTimed("no name", this.engine.getDocument(""));
        assertNotTimed("blank text", this.engine.putDocument("blank", "   "));
        assertNotTimed("long word", this.engine.putDocument("long", "a".repeat(201)));
        assertNotTimed("bad filter", this.engine.search("tree", "no tab here"));
        assertNotTimed("low byte limit", this.engine.setMaxBytes(1));
        assertNotTimed("nothing to undo for that name", this.engine.undoDocument("nothing"));
        this.engine.finishLoading();
        assertNotTimed("nothing to undo", this.engine.undoLast());
    }

    @Test
    void blankInput_NeverReachesTheStore() {
        assertNotTimed("blank search", this.engine.search("", ""));
        assertNotTimed("blank prefix", this.engine.searchPrefix(" ", ""));
        assertNotTimed("blank delete", this.engine.deleteAll("", ""));
        assertNotTimed("blank prefix delete", this.engine.deleteAllPrefix("", ""));
    }

    @Test
    void aCallTheStoreRejects_IsStillTimed() {
        String response = this.engine.setMaxDocuments(0);
        assertFalse(parse(response).get("ok").getAsBoolean());
        assertEquals(TICK, parse(response).get("storeNanos").getAsLong());
    }

    @Test
    void anEarlierTime_DoesNotLeakIntoALaterCall() {
        this.engine.search("tree", "");
        assertNotTimed("rejected after a timed call", this.engine.getDocument("Not Valid"));
    }

    @Test
    void theDefaultClock_KeepsResponsesDeterministic() {
        Engine plain = new Engine();
        assertEquals(0, parse(plain.putDocument("a", "text")).get("storeNanos").getAsLong());
        assertEquals(List.of("a"), ramNames(plain.snapshot()));
    }
}
