package edu.yu.cs.com1320.project.stage6;

import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the behaviour that only appears once documents move between RAM and disk.
 */
public class MemoryTierTest {

    @TempDir
    File baseDir;

    private DocumentStoreImpl store;

    @BeforeEach
    void setUp() {
        this.store = new DocumentStoreImpl(this.baseDir);
    }

    private static URI uri(String name) {
        return URI.create("http://docs.test/" + name);
    }

    private void put(String name, String text) throws IOException {
        put(this.store, name, text);
    }

    private static void put(DocumentStore target, String name, String text) throws IOException {
        target.put(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), uri(name), DocumentFormat.TXT);
    }

    private File fileFor(String name) {
        return new File(this.baseDir, "docs.test/" + name + ".json");
    }

    private boolean onDisk(String name) {
        return fileFor(name).exists();
    }

    private long filesOnDisk() throws IOException {
        try (Stream<Path> paths = Files.walk(this.baseDir.toPath())) {
            return paths.filter(path -> path.toString().endsWith(".json")).count();
        }
    }

    private static List<String> names(List<Document> docs) {
        List<String> names = new ArrayList<>();
        for (Document doc : docs) {
            String path = doc.getKey().getPath();
            names.add(path.substring(path.lastIndexOf('/') + 1));
        }
        return names;
    }

    // ===== B-Tree order, seen through the store =====

    @Test
    @DisplayName("documents added in reverse URI order all stay reachable")
    void put_DescendingUris_AllDocsReachable() throws IOException {
        for (int i = 24; i >= 1; i--) {
            put(String.format("doc-%03d", i), "text " + i);
        }
        for (int i = 1; i <= 24; i++) {
            assertNotNull(this.store.get(uri(String.format("doc-%03d", i))), "doc " + i + " was lost");
        }
    }

    @Test
    @DisplayName("documents added in any order all stay reachable")
    void put_ShuffledUris_AllDocsReachable() throws IOException {
        List<String> docNames = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            docNames.add(String.format("doc-%03d", i));
        }
        Random random = new Random(1320);
        for (int run = 0; run < 200; run++) {
            Collections.shuffle(docNames, random);
            DocumentStoreImpl fresh = new DocumentStoreImpl(this.baseDir);
            for (int i = 0; i < docNames.size(); i++) {
                put(fresh, docNames.get(i), "shared text");
                for (int j = 0; j <= i; j++) {
                    assertNotNull(fresh.get(uri(docNames.get(j))), "run " + run + " lost " + docNames.get(j));
                }
            }
            assertEquals(24, fresh.search("shared").size());
        }
    }

    // ===== Eviction =====

    @Test
    void eviction_WritesFile_AndReloadRemovesIt() throws IOException {
        put("a", "first doc");
        put("b", "second doc");
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"), "least recently used doc should be written to disk");
        assertFalse(onDisk("b"));

        assertEquals("first doc", this.store.get(uri("a")).getDocumentTxt());
        assertFalse(onDisk("a"), "reloaded doc should leave disk");
        assertTrue(onDisk("b"), "reloading should push the other doc out");
    }

    @Test
    void eviction_ChoosesLeastRecentlyUsed() throws IOException {
        put("a", "one");
        put("b", "two");
        put("c", "three");
        this.store.get(uri("a"));
        this.store.setMaxDocumentCount(2);
        assertTrue(onDisk("b"), "b was used longest ago");
        assertFalse(onDisk("a"));
        assertFalse(onDisk("c"));
    }

    @Test
    void eviction_ByteLimit_FreesOnlyWhatIsNeeded() throws IOException {
        put("a", "12345");
        put("b", "12345");
        put("c", "12345");
        this.store.setMaxDocumentBytes(10);
        assertEquals(1, filesOnDisk());
        assertTrue(onDisk("a"));
    }

    @Test
    void evictedDoc_KeepsMetadataAndRanking() throws IOException {
        put("a", "tree tree tree");
        put("b", "tree");
        this.store.setMetadata(uri("a"), "topic", "storage");
        this.store.get(uri("b"));
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"));

        assertEquals(List.of("a", "b"), names(this.store.search("tree")));
        assertEquals("storage", this.store.getMetadata(uri("a"), "topic"));
    }

    @Test
    void search_MoreMatchesThanRam_StillRanked() throws IOException {
        put("a", "tree");
        put("b", "tree tree tree");
        put("c", "tree tree");
        this.store.setMaxDocumentCount(1);
        assertEquals(List.of("b", "c", "a"), names(this.store.search("tree")));
        assertEquals(List.of("b", "c", "a"), names(this.store.searchByPrefix("tr")));
    }

    @Test
    void search_EqualCounts_OrderedByUri() throws IOException {
        List<String> expected = addShuffledDocs(30, "tree");
        assertEquals(expected, names(this.store.search("tree")));
        assertEquals(expected, names(this.store.searchByPrefix("tr")));
    }

    private List<String> addShuffledDocs(int count, String text) throws IOException {
        List<String> docNames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            docNames.add(String.format("doc-%03d", i));
        }
        List<String> shuffled = new ArrayList<>(docNames);
        Collections.shuffle(shuffled, new Random(1320));
        for (String name : shuffled) {
            put(name, text);
        }
        return docNames;
    }

    // ===== Undo across tiers =====

    @Test
    @DisplayName("undoing a put after the doc went to disk and back leaves the heap intact")
    void undoPut_AfterEvictionAndReload_KeepsHeapIntact() throws IOException {
        put("a", "one");
        put("b", "two");
        put("c", "three");
        this.store.setMaxDocumentCount(2);
        this.store.get(uri("a"));

        this.store.undo(uri("a"));

        assertNull(this.store.get(uri("a")));
        assertNotNull(this.store.get(uri("c")));
        assertNotNull(this.store.get(uri("b")));
    }

    @Test
    void undoPut_WhileDocIsOnDisk_RemovesFile() throws IOException {
        put("a", "one");
        put("b", "two");
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"));

        this.store.undo(uri("a"));

        assertFalse(onDisk("a"));
        assertNull(this.store.get(uri("a")));
        assertTrue(this.store.search("one").isEmpty());
    }

    @Test
    void undoDelete_OfDocOnDisk_RestoresIt() throws IOException {
        put("a", "one");
        put("b", "two");
        this.store.setMaxDocumentCount(1);
        assertTrue(this.store.delete(uri("a")));
        assertFalse(onDisk("a"));

        this.store.undo();

        assertEquals("one", this.store.get(uri("a")).getDocumentTxt());
    }

    @Test
    void undoBulkDelete_RespectsLimit() throws IOException {
        put("a", "shared one");
        put("b", "shared two");
        put("c", "shared three");
        this.store.setMaxDocumentCount(2);
        assertEquals(3, this.store.deleteAll("shared").size());
        assertEquals(0, filesOnDisk());

        this.store.undo();

        assertEquals(1, filesOnDisk(), "three docs restored with room for two in RAM");
        assertEquals(3, this.store.search("shared").size());
    }

    @Test
    void undoBulkDelete_RestoresInUriOrder() throws IOException {
        List<String> docNames = addShuffledDocs(30, "shared");
        this.store.deleteAll("shared");
        this.store.setMaxDocumentCount(5);

        this.store.undo();

        for (int i = 0; i < docNames.size(); i++) {
            assertEquals(i < 25, onDisk(docNames.get(i)), "docs come back in URI order, so the last five stay in RAM");
        }
    }

    @Test
    void undoMetadata_AfterEviction_RestoresValue() throws IOException {
        put("a", "one");
        put("b", "two");
        this.store.setMetadata(uri("a"), "topic", "first");
        this.store.setMetadata(uri("a"), "topic", "second");
        this.store.get(uri("b"));
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"));

        this.store.undo(uri("a"));

        assertEquals("first", this.store.getMetadata(uri("a"), "topic"));
    }

    // ===== Reads that must not move documents between tiers =====

    @Test
    void rejectedPut_LeavesDiskDocOnDisk() throws IOException {
        put("a", "12345");
        put("b", "12345");
        this.store.setMaxDocumentBytes(5);
        assertTrue(onDisk("a"));

        assertThrows(IllegalArgumentException.class, () -> put("a", "this text is over the limit"));

        assertTrue(onDisk("a"), "a rejected put must not pull the old doc into RAM");
        assertEquals(1, filesOnDisk());
    }

    @Test
    void deleteAllWithKeywordAndMetadata_LeavesNonMatchingDiskDocOnDisk() throws IOException {
        put("a", "shared");
        put("b", "shared");
        this.store.setMetadata(uri("b"), "topic", "keep");
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"));

        assertEquals(Set.of(uri("b")), this.store.deleteAllWithKeywordAndMetadata("shared", Map.of("topic", "keep")));

        assertTrue(onDisk("a"), "a did not match, so it must stay on disk");
    }

    @Test
    void deleteAllWithPrefixAndMetadata_LeavesNonMatchingDiskDocOnDisk() throws IOException {
        put("a", "shared");
        put("b", "shared");
        this.store.setMetadata(uri("b"), "topic", "keep");
        this.store.setMaxDocumentCount(1);

        assertEquals(Set.of(uri("b")), this.store.deleteAllWithPrefixAndMetadata("sha", Map.of("topic", "keep")));

        assertTrue(onDisk("a"));
    }

    @Test
    void deleteAllWithMetadata_LeavesNonMatchingDiskDocOnDisk() throws IOException {
        put("a", "one");
        put("b", "two");
        this.store.setMetadata(uri("b"), "topic", "keep");
        this.store.setMaxDocumentCount(1);

        assertEquals(Set.of(uri("b")), this.store.deleteAllWithMetadata(Map.of("topic", "keep")));

        assertTrue(onDisk("a"));
    }

    @Test
    void metadataSearches_LeaveNonMatchingDiskDocOnDisk() throws IOException {
        put("a", "shared");
        put("b", "shared");
        this.store.setMetadata(uri("b"), "topic", "keep");
        this.store.setMaxDocumentCount(1);
        Map<String, String> filter = Map.of("topic", "keep");

        assertEquals(List.of("b"), names(this.store.searchByMetadata(filter)));
        assertTrue(onDisk("a"));
        assertEquals(List.of("b"), names(this.store.searchByKeywordAndMetadata("shared", filter)));
        assertTrue(onDisk("a"));
        assertEquals(List.of("b"), names(this.store.searchByPrefixAndMetadata("sha", filter)));
        assertTrue(onDisk("a"));
    }

    @Test
    void metadataSearch_NoMatch_LeavesLruOrderAlone() throws IOException {
        for (String name : List.of("e", "c", "a", "d", "b")) {
            put(name, "text");
        }
        assertTrue(this.store.searchByMetadata(Map.of("topic", "none")).isEmpty());

        this.store.setMaxDocumentCount(3);

        assertTrue(onDisk("e"), "e and c are the oldest and nothing used them");
        assertTrue(onDisk("c"));
    }

    @Test
    void keywordFilter_LeavesFilteredOutDocOld() throws IOException {
        put("a", "shared");
        put("b", "shared");
        put("c", "shared");
        this.store.setMetadata(uri("c"), "topic", "keep");
        this.store.get(uri("b"));

        this.store.searchByKeywordAndMetadata("shared", Map.of("topic", "keep"));
        this.store.setMaxDocumentCount(2);

        assertTrue(onDisk("a"), "a was filtered out, so it is still the oldest");
    }

    @Test
    void search_DoesNotTouchNonMatchingDocs() throws IOException {
        put("a", "other");
        put("b", "tree");
        put("c", "tree");
        this.store.search("tree");
        this.store.setMaxDocumentCount(2);
        assertTrue(onDisk("a"));
    }

    @Test
    void fileLeftByEarlierRun_IsNotTreatedAsStoredDoc() throws IOException {
        put("a", "old text");
        put("b", "other");
        this.store.setMaxDocumentCount(1);
        assertTrue(onDisk("a"));

        DocumentStoreImpl restarted = new DocumentStoreImpl(this.baseDir);

        assertFalse(restarted.delete(uri("a")));
        assertEquals(0, restarted.put(new ByteArrayInputStream("new text".getBytes()), uri("a"), DocumentFormat.TXT));
    }

    // ===== Docs larger than a lowered byte limit =====

    @Test
    void setMetadata_DocLargerThanLoweredLimit_IsSaved() throws IOException {
        put("a", "this document is long");
        this.store.setMaxDocumentBytes(5);
        assertTrue(onDisk("a"));

        assertNull(this.store.setMetadata(uri("a"), "topic", "big"));

        assertEquals("big", this.store.getMetadata(uri("a"), "topic"));
        this.store.undo();
        assertNull(this.store.getMetadata(uri("a"), "topic"));
    }

    // ===== Input a search box can send =====

    @Test
    void nonLatinInput_FindsNothingAndDoesNotThrow() throws IOException {
        put("a", "plain text");
        for (String query : List.of("日本語", "😀", "€uro")) {
            assertTrue(this.store.search(query).isEmpty());
            assertTrue(this.store.searchByPrefix(query).isEmpty());
            assertTrue(this.store.deleteAll(query).isEmpty());
            assertTrue(this.store.deleteAllWithPrefix(query).isEmpty());
            assertTrue(this.store.searchByKeywordAndMetadata(query, Map.of("k", "v")).isEmpty());
        }
        assertNotNull(this.store.get(uri("a")));
    }

    @Test
    void blankPrefix_MatchesNothing() throws IOException {
        put("a", "plain text");
        assertTrue(this.store.searchByPrefix("").isEmpty());
        assertTrue(this.store.searchByPrefix("  ").isEmpty());
        assertTrue(this.store.deleteAllWithPrefix("").isEmpty());
        assertTrue(this.store.deleteAllWithPrefix("  ").isEmpty());
        assertNotNull(this.store.get(uri("a")), "a blank prefix must not delete anything");
    }

    @Test
    void deleteOfMissingDoc_LeavesNothingToUndo() {
        assertFalse(this.store.delete(uri("missing")));
        assertThrows(IllegalStateException.class, () -> this.store.undo());
    }

    // ===== Randomized check against a plain reference model =====

    @Test
    @DisplayName("random operations always match a simple model and respect the limit")
    void randomOperations_MatchModel() throws IOException {
        for (int seed = 1; seed <= Integer.getInteger("modelSeeds", 60); seed++) {
            new ModelCheck(seed).run();
        }
    }

    /**
     * Runs random operations against the store and against a plain map, then compares them after every step.
     * Each history entry records what the docs it touched looked like before the operation, so undo is a lookup.
     */
    private class ModelCheck {
        private final int seed;
        private final Random random;
        private final File dir;
        private final DocumentStoreImpl target;
        private final boolean byteLimit;
        private final int limit;
        private final Map<String, ModelDoc> model = new HashMap<>();
        private final Deque<Map<String, ModelDoc>> history = new ArrayDeque<>();

        private ModelCheck(int seed) throws IOException {
            this.seed = seed;
            this.random = new Random(seed);
            this.dir = Files.createTempDirectory(baseDir.toPath(), "seed").toFile();
            this.target = new DocumentStoreImpl(this.dir);
            this.byteLimit = this.random.nextBoolean();
            this.limit = this.byteLimit ? 60 + this.random.nextInt(60) : 2 + this.random.nextInt(4);
            if (this.byteLimit) {
                this.target.setMaxDocumentBytes(this.limit);
            } else {
                this.target.setMaxDocumentCount(this.limit);
            }
        }

        private void run() throws IOException {
            String[] words = {"tree", "trie", "heap", "stack"};
            for (int step = 0; step < 300; step++) {
                String name = String.format("doc-%02d", this.random.nextInt(14));
                String word = words[this.random.nextInt(words.length)];
                String context = "seed " + this.seed + " step " + step;
                applyRandomOperation(name, word, step, context);
                assertTiersConsistent(context);
            }
            for (Map.Entry<String, ModelDoc> entry : this.model.entrySet()) {
                Document doc = this.target.get(uri(entry.getKey()));
                assertEquals(entry.getValue().text, doc.getDocumentTxt(), "seed " + this.seed);
                assertEquals(entry.getValue().metadata.get("topic"), doc.getMetadataValue("topic"), "seed " + this.seed);
            }
        }

        private void applyRandomOperation(String name, String word, int step, String context) throws IOException {
            switch (this.random.nextInt(11)) {
                case 0, 1 -> {
                    String text = word + " body " + step;
                    remember(List.of(name));
                    put(this.target, name, text);
                    this.model.put(name, new ModelDoc(text, new HashMap<>()));
                }
                case 2 -> assertEquals(this.model.containsKey(name), this.target.get(uri(name)) != null, context);
                case 3 -> {
                    if (this.model.containsKey(name)) {
                        remember(List.of(name));
                        this.model.remove(name);
                    }
                    this.target.delete(uri(name));
                }
                case 4 -> {
                    if (this.model.containsKey(name)) {
                        remember(List.of(name));
                        this.model.get(name).metadata.put("topic", word);
                        this.target.setMetadata(uri(name), "topic", word);
                    }
                }
                case 5 -> {
                    List<String> matches = namesMatching(word);
                    assertEquals(matches.size(), this.target.deleteAll(word).size(), context);
                    removeFromModel(matches);
                }
                case 6 -> {
                    String prefix = word.substring(0, 2);
                    List<String> matches = namesMatching(prefix);
                    assertEquals(matches.size(), this.target.deleteAllWithPrefix(prefix).size(), context);
                    removeFromModel(matches);
                }
                case 7 -> {
                    if (this.history.isEmpty()) {
                        assertThrows(IllegalStateException.class, this.target::undo, context);
                    } else {
                        this.target.undo();
                        Map<String, ModelDoc> before = this.history.pop();
                        for (String docName : before.keySet()) {
                            restore(docName, before);
                        }
                    }
                }
                case 8 -> undoOneDoc(name, context);
                default -> {
                    assertEquals(namesMatching(word).size(), this.target.searchByPrefix(word).size(), context);
                    long tagged = this.model.values().stream().filter(doc -> word.equals(doc.metadata.get("topic"))).count();
                    assertEquals(tagged, this.target.searchByMetadata(Map.of("topic", word)).size(), context);
                }
            }
        }

        private void undoOneDoc(String name, String context) {
            for (Map<String, ModelDoc> entry : this.history) {
                if (entry.containsKey(name)) {
                    this.target.undo(uri(name));
                    restore(name, entry);
                    entry.remove(name);
                    this.history.removeIf(Map::isEmpty);
                    return;
                }
            }
            assertThrows(IllegalStateException.class, () -> this.target.undo(uri(name)), context);
        }

        private void restore(String name, Map<String, ModelDoc> before) {
            if (before.get(name) == null) {
                this.model.remove(name);
            } else {
                this.model.put(name, before.get(name));
            }
        }

        private List<String> namesMatching(String prefix) {
            List<String> matches = new ArrayList<>();
            for (Map.Entry<String, ModelDoc> entry : this.model.entrySet()) {
                if (entry.getValue().text.startsWith(prefix)) {
                    matches.add(entry.getKey());
                }
            }
            return matches;
        }

        private void removeFromModel(List<String> names) {
            if (!names.isEmpty()) {
                remember(names);
                this.model.keySet().removeAll(names);
            }
        }

        private void remember(List<String> names) {
            Map<String, ModelDoc> before = new HashMap<>();
            for (String name : names) {
                ModelDoc doc = this.model.get(name);
                before.put(name, doc == null ? null : new ModelDoc(doc.text, new HashMap<>(doc.metadata)));
            }
            this.history.push(before);
        }

        private void assertTiersConsistent(String context) throws IOException {
            long ramDocs = 0;
            long ramBytes = 0;
            for (Map.Entry<String, ModelDoc> entry : this.model.entrySet()) {
                if (!new File(this.dir, "docs.test/" + entry.getKey() + ".json").exists()) {
                    ramDocs++;
                    ramBytes += entry.getValue().text.length();
                }
            }
            long files;
            try (Stream<Path> paths = Files.walk(this.dir.toPath())) {
                files = paths.filter(path -> path.toString().endsWith(".json")).count();
            }
            assertEquals(this.model.size() - ramDocs, files, context + ": a file exists for a doc that isn't stored");
            assertTrue((this.byteLimit ? ramBytes : ramDocs) <= this.limit, context + ": RAM limit exceeded");
        }
    }

    private static class ModelDoc {
        private final String text;
        private final Map<String, String> metadata;

        private ModelDoc(String text, Map<String, String> metadata) {
            this.text = text;
            this.metadata = metadata;
        }
    }
}
