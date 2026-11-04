package edu.yu.cs.com1320.project.demo;

import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static edu.yu.cs.com1320.project.demo.Responses.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The panels redraw from snapshots all the time, so a snapshot must leave no trace on the engine.
 */
public class SnapshotPurityTest {

    private static void load(Engine engine) {
        for (int i = 0; i < 10; i++) {
            engine.putDocument("doc-" + i, "shared words number" + i);
        }
        engine.setMaxDocuments(6);
        engine.getDocument("doc-1"); // from disk
        engine.getDocument("doc-7"); // already in RAM
        engine.search("number8", "");
    }

    private static void read(Engine engine) {
        engine.snapshot();
        engine.indexView("numb");
        for (int i = 0; i < 10; i++) {
            engine.diskJson("doc-" + i);
        }
    }

    @Test
    void reads_MakeNoDiskCalls() {
        Engine engine = new Engine();
        load(engine);
        engine.disk().clearCalls();
        read(engine);
        assertEquals(0, engine.disk().calls().size());
    }

    @Test
    void reads_LeaveTiersAlone() {
        Engine engine = new Engine();
        load(engine);
        String before = engine.snapshot();
        for (int i = 0; i < 50; i++) {
            read(engine);
        }
        assertEquals(before, engine.snapshot());
    }

    /**
     * Two engines take the same calls, but one is read constantly. If a read touched last-use times or
     * the heap, the two would evict in a different order.
     */
    @Test
    void reads_DoNotChangeWhatGetsEvictedNext() {
        Engine quiet = new Engine();
        Engine watched = new Engine();
        load(quiet);
        load(watched);
        for (int i = 0; i < 50; i++) {
            read(watched);
        }
        List<String> quietOrder = new ArrayList<>();
        List<String> watchedOrder = new ArrayList<>();
        for (int limit = 5; limit >= 1; limit--) {
            quietOrder.addAll(strings(events(quiet.setMaxDocuments(limit)).getAsJsonArray("evicted")));
            read(watched);
            watchedOrder.addAll(strings(events(watched.setMaxDocuments(limit)).getAsJsonArray("evicted")));
        }
        assertEquals(5, quietOrder.size());
        assertEquals(quietOrder, watchedOrder);
    }

    /**
     * Every Engine response carries a snapshot, so two Engines can't reveal a snapshot that disturbs the
     * store: both would be disturbed alike. The baseline here is a store with no Engine around it.
     * After a search, results share one last use time and only the heap's layout decides between them.
     * They're ranked highest count first, so that layout runs against the order of the names.
     */
    @Test
    void wrappingTheStore_DoesNotChangeWhatItEvicts() throws IOException {
        BrowserPersistenceManager disk = new BrowserPersistenceManager();
        DocumentStoreImpl store = new DocumentStoreImpl(disk);
        Engine engine = new Engine();
        for (int i = 9; i >= 0; i--) {
            String text = "shared ".repeat(i + 1);
            store.put(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), URI.create("http://demo.local/doc-" + i), DocumentFormat.TXT);
            engine.putDocument("doc-" + i, text);
        }
        store.search("shared");
        engine.search("shared", "");

        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        for (int limit = 9; limit >= 1; limit--) {
            store.setMaxDocumentCount(limit);
            actual.addAll(strings(events(engine.setMaxDocuments(limit)).getAsJsonArray("evicted")));
        }
        for (BrowserPersistenceManager.Call call : disk.calls()) {
            if (call.kind == BrowserPersistenceManager.CallKind.WRITE) {
                expected.add(call.uri.toString().substring("http://demo.local/".length()));
            }
        }
        assertEquals(9, expected.size());
        assertEquals(expected, actual);
    }
}
