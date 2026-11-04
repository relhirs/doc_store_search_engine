package edu.yu.cs.com1320.project.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import edu.yu.cs.com1320.project.demo.BrowserPersistenceManager.Call;
import edu.yu.cs.com1320.project.demo.BrowserPersistenceManager.CallKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static edu.yu.cs.com1320.project.demo.Responses.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The memory wall animates the event feed, so it has to agree with what the engine really did. Events
 * are built from the tiers, so these checks use something else: the log of disk calls, and the
 * totals the store keeps for itself.
 */
public class EventFeedTest {

    private static final String[] WORDS = {"tree", "trees", "trie", "heap", "heaps", "disk", "disks", "memory", "merge", "stack"};
    private static final String[] TOPICS = {"trees", "heaps", "storage"};

    /** Everything a visitor's call can legitimately be refused for. Any other error is a bug in the engine. */
    private static final List<String> EXPECTED_ERRORS = List.of(
            "No document stored at that URI",
            "There is nothing to undo",
            "There is nothing to undo for that document",
            "Doc being added can't exceed threshold for doc size");
    private static final String BYTE_LIMIT_ERROR = "The limit can't go below the largest document";

    private static int evictions;
    private static int reloads;

    private static String nameOf(Call call) {
        return call.uri.toString().substring("http://demo.local/".length());
    }

    private static String text(Random random) {
        StringBuilder text = new StringBuilder();
        for (int i = 3 + random.nextInt(12); i > 0; i--) {
            text.append(WORDS[random.nextInt(WORDS.length)]).append(' ');
        }
        return text.toString();
    }

    private static String randomCall(Engine engine, Random random) {
        String name = "doc-" + random.nextInt(24);
        String word = WORDS[random.nextInt(WORDS.length)];
        String prefix = word.substring(0, 1 + random.nextInt(word.length()));
        String filter = "topic\t" + TOPICS[random.nextInt(TOPICS.length)];
        switch (random.nextInt(14)) {
            case 0: case 1: return engine.putDocument(name, text(random));
            case 2: case 3: return engine.getDocument(name);
            case 4: return engine.deleteDocument(name);
            case 5: return engine.undoLast();
            case 6: return engine.undoDocument(name);
            case 7: return engine.search(word, "");
            case 8: return engine.searchPrefix(prefix, random.nextBoolean() ? "" : filter);
            case 9: return engine.deleteAll(word, "");
            case 10: return engine.deleteAllPrefix(prefix, filter);
            case 11: return engine.tagDocument(name, "topic", TOPICS[random.nextInt(TOPICS.length)]);
            case 12: return engine.setMaxDocuments(1 + random.nextInt(20));
            default: return engine.setMaxBytes(100 + random.nextInt(1500));
        }
    }

    private static List<String> lastCallOrder(List<Call> calls, CallKind kind, Set<String> names) {
        Map<String, Integer> last = new HashMap<>();
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).kind == kind && names.contains(nameOf(calls.get(i)))) {
                last.put(nameOf(calls.get(i)), i);
            }
        }
        List<String> order = new ArrayList<>(last.keySet());
        order.sort((a, b) -> last.get(a) - last.get(b));
        return order;
    }

    private static void checkNoInternalError(String response) {
        JsonObject parsed = parse(response);
        if (!parsed.get("ok").getAsBoolean()) {
            String error = parsed.get("error").getAsString();
            assertTrue(EXPECTED_ERRORS.contains(error) || error.startsWith(BYTE_LIMIT_ERROR), "internal error: " + error);
        }
    }

    private static void checkEvents(String response, List<Call> calls, Set<String> ramBefore, Set<String> diskBefore) {
        JsonObject events = events(response);
        Set<String> ramAfter = new HashSet<>(ramNames(response));
        Set<String> diskAfter = new HashSet<>(diskNames(response));

        List<String> evicted = strings(events.getAsJsonArray("evicted"));
        assertEquals(lastCallOrder(calls, CallKind.WRITE, new HashSet<>(evicted)), evicted, "every eviction is a write, in write order");
        evictions += evicted.size();

        List<String> loaded = strings(events.getAsJsonArray("loaded"));
        assertEquals(lastCallOrder(calls, CallKind.DELETE, new HashSet<>(loaded)), loaded, "every reload leaves the disk, in that order");
        for (String name : loaded) {
            assertTrue(lastCallOrder(calls, CallKind.READ, Set.of(name)).contains(name), "a reload reads the document");
        }
        reloads += loaded.size();

        Set<String> expectedRam = new HashSet<>(ramBefore);
        Set<String> expectedDisk = new HashSet<>(diskBefore);
        for (String name : evicted) {
            assertTrue(expectedRam.remove(name), name + " was evicted but wasn't in RAM");
            expectedDisk.add(name);
        }
        for (String name : loaded) {
            assertTrue(expectedDisk.remove(name), name + " was reloaded but wasn't on disk");
            expectedRam.add(name);
        }
        for (JsonElement element : events.getAsJsonArray("deleted")) {
            JsonObject deleted = element.getAsJsonObject();
            Set<String> tier = deleted.get("tier").getAsString().equals("ram") ? expectedRam : expectedDisk;
            assertTrue(tier.remove(deleted.get("name").getAsString()), "deleted from the wrong tier");
        }
        for (JsonElement element : events.getAsJsonArray("added")) {
            JsonObject added = element.getAsJsonObject();
            Set<String> tier = added.get("tier").getAsString().equals("ram") ? expectedRam : expectedDisk;
            assertTrue(tier.add(added.get("name").getAsString()), "added a document that already existed");
        }
        assertEquals(expectedRam, ramAfter, "applying the events to the old RAM gives the new RAM");
        assertEquals(expectedDisk, diskAfter, "applying the events to the old disk gives the new disk");
    }

    private static void checkTotals(JsonObject state) {
        JsonArray ram = state.getAsJsonArray("ram");
        int bytes = 0;
        for (JsonElement tile : ram) {
            bytes += tile.getAsJsonObject().get("bytes").getAsInt();
        }
        assertEquals(ram.size(), state.get("ramCount").getAsInt(), "the store's count matches RAM");
        assertEquals(bytes, state.get("ramBytes").getAsInt(), "the store's bytes match RAM");
        int maxDocuments = state.get("maxDocuments").getAsInt();
        int maxBytes = state.get("maxBytes").getAsInt();
        assertTrue(maxDocuments == -1 || ram.size() <= maxDocuments, "document limit held");
        assertTrue(maxBytes == -1 || bytes <= maxBytes, "byte limit held");
        if (ram.size() > 0) {
            assertTrue(names(ram).contains(state.get("nextToEvict").getAsString()), "next to evict is in RAM");
        } else {
            assertTrue(state.get("nextToEvict").isJsonNull());
        }
    }

    @Test
    void events_AgreeWithTheDiskAndTheTotalsAfterEveryCall() {
        for (int seed = 0; seed < 150; seed++) {
            Random random = new Random(seed);
            Engine engine = new Engine();
            for (int i = 0; i < 16; i++) {
                engine.putDocument("doc-" + i, text(random));
                engine.tagDocument("doc-" + i, "topic", TOPICS[i % TOPICS.length]);
            }
            String last = engine.finishLoading();
            for (int step = 0; step < 300; step++) {
                Set<String> ramBefore = new HashSet<>(ramNames(last));
                Set<String> diskBefore = new HashSet<>(diskNames(last));
                last = randomCall(engine, random);
                checkNoInternalError(last);
                checkEvents(last, engine.disk().calls(), ramBefore, diskBefore);
                checkTotals(state(last));
            }
        }
        assertTrue(evictions > 1000, "the run should exercise eviction, got " + evictions);
        assertTrue(reloads > 1000, "the run should exercise reloads, got " + reloads);
    }

    /** The eviction panel names the next victim, so that has to be right after any history of calls. */
    @Test
    void nextToEvict_PredictsTheNextEviction() {
        int predictions = 0;
        for (int seed = 0; seed < 100; seed++) {
            Random random = new Random(seed);
            Engine engine = new Engine();
            for (int i = 0; i < 16; i++) {
                engine.putDocument("doc-" + i, text(random));
                engine.tagDocument("doc-" + i, "topic", TOPICS[i % TOPICS.length]);
            }
            engine.finishLoading();
            for (int step = 0; step < 200; step++) {
                checkNoInternalError(randomCall(engine, random));
                if (step % 10 != 9) {
                    continue;
                }
                JsonObject state = state(engine.snapshot());
                int ramCount = state.get("ramCount").getAsInt();
                if (ramCount < 2) {
                    continue;
                }
                String predicted = state.get("nextToEvict").getAsString();
                List<String> evicted = strings(events(engine.setMaxDocuments(ramCount - 1)).getAsJsonArray("evicted"));
                assertEquals(List.of(predicted), evicted);
                predictions++;
            }
        }
        assertTrue(predictions > 500, "the run should make predictions, got " + predictions);
    }
}
