package edu.yu.cs.com1320.project.demo;

import edu.yu.cs.com1320.project.demo.BrowserPersistenceManager.Call;
import edu.yu.cs.com1320.project.demo.BrowserPersistenceManager.CallKind;
import edu.yu.cs.com1320.project.demo.BrowserPersistenceManager.Stored;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * The store as the web demo sees it. Every call returns a JSON string with the result, the documents
 * that changed tier during the call, and the state afterwards. The output depends only on the calls
 * made, so the same calls give the same bytes on the JVM and under TeaVM. The one exception is
 * storeNanos, which is why the clock is passed in: only the shipped engine is given a real one.
 */
final class Engine {

    private static final String BASE_URI = "http://demo.local/";
    private static final int MAX_TEXT = 20000;
    private static final int MAX_WORD = 200;

    private enum Tier {
        RAM, DISK
    }

    private interface Action {
        String run() throws IOException;
    }

    private interface StoreCall<T> {
        T run() throws IOException;
    }

    private final LongSupplier clock;
    private final BrowserPersistenceManager disk = new BrowserPersistenceManager();
    private final DocumentStoreImpl store = new DocumentStoreImpl(this.disk);
    // By identity: two commands on one URI are equal, and a CommandSet's hash changes as it's undone
    private final Map<Undoable, String> labels = new IdentityHashMap<>();
    private int maxDocuments = -1;
    private int maxBytes = -1;
    private int loadedCommands = 0;
    // Null until the call reaches the store, because the bridge can reject it first
    private Long storeNanos = null;

    Engine() {
        this(() -> 0L);
    }

    Engine(LongSupplier clock) {
        this.clock = clock;
    }

    BrowserPersistenceManager disk() {
        return this.disk;
    }

    /** Names become URIs here, so URIs stay lowercase: TeaVM's URI compares scheme and host by case. */
    private static URI uri(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("A document needs a name");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-')) {
                throw new IllegalArgumentException("Document names use lowercase letters, digits and dashes");
            }
        }
        return URI.create(BASE_URI + name);
    }

    private static String name(URI uri) {
        return uri.toString().substring(BASE_URI.length());
    }

    /**
     * @param filters one "key\tvalue" pair per line, or empty for none
     */
    private static Map<String, String> filters(String filters) {
        Map<String, String> parsed = new TreeMap<>();
        if (filters == null || filters.isEmpty()) {
            return parsed;
        }
        for (String line : filters.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab < 1) {
                throw new IllegalArgumentException("Filters are written as key, tab, value");
            }
            parsed.put(line.substring(0, tab), line.substring(tab + 1));
        }
        return parsed;
    }

    private String withFilters(String filterLines, Function<Map<String, String>, String> call) {
        Map<String, String> filters;
        try {
            filters = filters(filterLines);
        } catch (IllegalArgumentException e) {
            return run(null, () -> {
                throw e;
            });
        }
        return call.apply(filters);
    }

    private static String mapCode(Map<String, String> filters) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            parts.add(Json.quote(entry.getKey()));
            parts.add(Json.quote(entry.getValue()));
        }
        return "Map.of(" + String.join(", ", parts) + ")";
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * The trie recurses once per letter and gives every node 256 slots, so one long word overflows the
     * stack and one big paste can take hundreds of megabytes from a visitor's browser tab.
     */
    private static void checkSize(String text) {
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A document can hold up to " + MAX_TEXT + " characters");
        }
        int word = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
            word = letter ? word + 1 : 0;
            if (word > MAX_WORD) {
                throw new IllegalArgumentException("A word can be up to " + MAX_WORD + " characters");
            }
        }
    }

    String putDocument(String name, String text) {
        return run("store.put(text, uri(" + Json.quote(name) + "), TXT)", () -> {
            if (isBlank(text)) {
                throw new IllegalArgumentException("A document needs some text");
            }
            checkSize(text);
            ByteArrayInputStream input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
            URI target = uri(name);
            int previous = timed(() -> this.store.put(input, target, DocumentFormat.TXT));
            return previous == 0 ? "\"new\"" : "\"replaced\"";
        });
    }

    String tagDocument(String name, String key, String value) {
        return run("store.setMetadata(uri(" + Json.quote(name) + "), " + Json.quote(key) + ", " + Json.quote(value) + ")",
                () -> {
                    URI target = uri(name);
                    return Json.quote(timed(() -> this.store.setMetadata(target, key, value)));
                });
    }

    /** Everything on the undo stack so far is the demo's own loading, so it stays out of the visitor's history. */
    String finishLoading() {
        this.loadedCommands = this.store.undoHistory().size();
        return snapshot();
    }

    String getDocument(String name) {
        return run("store.get(uri(" + Json.quote(name) + "))", () -> {
            URI target = uri(name);
            Document doc = timed(() -> this.store.get(target));
            return doc == null ? "null" : row(doc, null);
        });
    }

    String deleteDocument(String name) {
        return run("store.delete(uri(" + Json.quote(name) + "))", () -> {
            URI target = uri(name);
            return String.valueOf(timed(() -> this.store.delete(target)));
        });
    }

    String undoLast() {
        return run("store.undo()", () -> {
            if (visibleHistory().isEmpty()) {
                throw new IllegalStateException("There is nothing to undo");
            }
            timed(() -> {
                this.store.undo();
                return null;
            });
            return "null";
        });
    }

    String undoDocument(String name) {
        return run("store.undo(uri(" + Json.quote(name) + "))", () -> {
            URI target = uri(name);
            boolean found = false;
            for (Undoable command : visibleHistory()) {
                found |= targets(command).contains(name(target));
            }
            if (!found) {
                throw new IllegalStateException("There is nothing to undo for that document");
            }
            timed(() -> {
                this.store.undo(target);
                return null;
            });
            return "null";
        });
    }

    String search(String keyword, String filterLines) {
        return withFilters(filterLines, filters -> search(keyword, filters));
    }

    private String search(String keyword, Map<String, String> filters) {
        if (isBlank(keyword)) {
            return filters.isEmpty() ? empty() : searchMetadata(filters);
        }
        ToIntFunction<Document> count = doc -> doc.wordCount(keyword);
        if (filters.isEmpty()) {
            return run("store.search(" + Json.quote(keyword) + ")", () -> rows(timed(() -> this.store.search(keyword)), count));
        }
        return run("store.searchByKeywordAndMetadata(" + Json.quote(keyword) + ", " + mapCode(filters) + ")",
                () -> rows(timed(() -> this.store.searchByKeywordAndMetadata(keyword, filters)), count));
    }

    String searchPrefix(String prefix, String filterLines) {
        return withFilters(filterLines, filters -> searchPrefix(prefix, filters));
    }

    private String searchPrefix(String prefix, Map<String, String> filters) {
        if (isBlank(prefix)) {
            return filters.isEmpty() ? empty() : searchMetadata(filters);
        }
        ToIntFunction<Document> count = doc -> prefixCount(doc, prefix);
        if (filters.isEmpty()) {
            return run("store.searchByPrefix(" + Json.quote(prefix) + ")", () -> rows(timed(() -> this.store.searchByPrefix(prefix)), count));
        }
        return run("store.searchByPrefixAndMetadata(" + Json.quote(prefix) + ", " + mapCode(filters) + ")",
                () -> rows(timed(() -> this.store.searchByPrefixAndMetadata(prefix, filters)), count));
    }

    private String searchMetadata(Map<String, String> filters) {
        return run("store.searchByMetadata(" + mapCode(filters) + ")", () -> rows(timed(() -> this.store.searchByMetadata(filters)), null));
    }

    private static int prefixCount(Document doc, String prefix) {
        int total = 0;
        for (String word : doc.getWords()) {
            if (word.startsWith(prefix)) {
                total += doc.wordCount(word);
            }
        }
        return total;
    }

    String deleteAll(String keyword, String filterLines) {
        return withFilters(filterLines, filters -> deleteAll(keyword, filters));
    }

    private String deleteAll(String keyword, Map<String, String> filters) {
        if (isBlank(keyword)) {
            return filters.isEmpty() ? empty() : deleteMetadata(filters);
        }
        if (filters.isEmpty()) {
            return run("store.deleteAll(" + Json.quote(keyword) + ")", () -> sortedNames(timed(() -> this.store.deleteAll(keyword))));
        }
        return run("store.deleteAllWithKeywordAndMetadata(" + Json.quote(keyword) + ", " + mapCode(filters) + ")",
                () -> sortedNames(timed(() -> this.store.deleteAllWithKeywordAndMetadata(keyword, filters))));
    }

    String deleteAllPrefix(String prefix, String filterLines) {
        return withFilters(filterLines, filters -> deleteAllPrefix(prefix, filters));
    }

    private String deleteAllPrefix(String prefix, Map<String, String> filters) {
        if (isBlank(prefix)) {
            return filters.isEmpty() ? empty() : deleteMetadata(filters);
        }
        if (filters.isEmpty()) {
            return run("store.deleteAllWithPrefix(" + Json.quote(prefix) + ")", () -> sortedNames(timed(() -> this.store.deleteAllWithPrefix(prefix))));
        }
        return run("store.deleteAllWithPrefixAndMetadata(" + Json.quote(prefix) + ", " + mapCode(filters) + ")",
                () -> sortedNames(timed(() -> this.store.deleteAllWithPrefixAndMetadata(prefix, filters))));
    }

    private String deleteMetadata(Map<String, String> filters) {
        return run("store.deleteAllWithMetadata(" + mapCode(filters) + ")", () -> sortedNames(timed(() -> this.store.deleteAllWithMetadata(filters))));
    }

    String setMaxDocuments(int limit) {
        return run("store.setMaxDocumentCount(" + limit + ")", () -> {
            timed(() -> {
                this.store.setMaxDocumentCount(limit);
                return null;
            });
            this.maxDocuments = limit;
            return "null";
        });
    }

    String setMaxBytes(int limit) {
        return run("store.setMaxDocumentBytes(" + limit + ")", () -> {
            int largest = largestDocumentBytes();
            if (limit < largest) {
                throw new IllegalArgumentException("The limit can't go below the largest document, which is " + largest + " bytes");
            }
            timed(() -> {
                this.store.setMaxDocumentBytes(limit);
                return null;
            });
            this.maxBytes = limit;
            return "null";
        });
    }

    private int largestDocumentBytes() {
        int largest = 0;
        for (URI uri : this.store.urisInRamByLastUse()) {
            largest = Math.max(largest, bytes(this.store.documentInRam(uri)));
        }
        for (URI uri : this.store.urisOnDisk()) {
            largest = Math.max(largest, this.disk.stored(uri).bytes);
        }
        return largest;
    }

    String snapshot() {
        return "{\"ok\":true,\"state\":" + state() + "}";
    }

    /**
     * @return the JSON stored for a document on disk, or a null result if it isn't on disk
     */
    String diskJson(String name) {
        try {
            Stored stored = this.disk.stored(uri(name));
            return "{\"ok\":true,\"result\":" + (stored == null ? "null" : Json.quote(stored.json)) + "}";
        } catch (IllegalArgumentException e) {
            return "{\"ok\":false,\"error\":" + Json.quote(e.getMessage()) + "}";
        }
    }

    /**
     * @return the word index along this text, one level per letter it can follow. Like a snapshot, this
     * has no effect on the store.
     */
    String indexView(String text) {
        String typed = text == null ? "" : text;
        List<String> levels = new ArrayList<>();
        List<Character> next = List.of();
        for (int end = 0; end <= typed.length(); end++) {
            if (end > 0 && !next.contains(typed.charAt(end - 1))) {
                break;
            }
            String prefix = typed.substring(0, end);
            next = this.store.nextIndexCharacters(prefix);
            List<String> letters = new ArrayList<>();
            for (char letter : next) {
                letters.add(String.valueOf(letter));
            }
            levels.add("{\"prefix\":" + Json.quote(prefix) + ",\"next\":" + Json.stringArray(letters)
                    + ",\"documents\":" + this.store.documentsWithWord(prefix) + "}");
        }
        return "{\"ok\":true,\"result\":" + Json.array(levels) + "}";
    }

    private String empty() {
        return "{\"ok\":true,\"call\":null,\"storeNanos\":null,\"result\":[],\"events\":" + events(tiers(), tiers()) + ",\"state\":" + state() + "}";
    }

    /** Wraps the store call alone, so storeNanos doesn't count the bridge's work on either side of it. */
    private <T> T timed(StoreCall<T> call) throws IOException {
        long start = this.clock.getAsLong();
        try {
            return call.run();
        } finally {
            this.storeNanos = this.clock.getAsLong() - start;
        }
    }

    /** Errors are reported here too: under TeaVM an exception reaches JavaScript with its class name minified. */
    private String run(String call, Action action) {
        Map<String, Tier> before = tiers();
        this.disk.clearCalls();
        this.storeNanos = null;
        String result = null;
        String error = null;
        try {
            result = action.run();
        } catch (IOException | RuntimeException e) {
            error = e.getMessage() != null ? e.getMessage() : "The store rejected that call";
        }
        labelNewCommands(call);
        StringBuilder out = new StringBuilder("{\"ok\":").append(error == null);
        out.append(",\"call\":").append(Json.quote(call));
        out.append(",\"storeNanos\":").append(this.storeNanos);
        if (error == null) {
            out.append(",\"result\":").append(result);
        } else {
            out.append(",\"error\":").append(Json.quote(error));
        }
        out.append(",\"events\":").append(events(before, tiers()));
        return out.append(",\"state\":").append(state()).append('}').toString();
    }

    private void labelNewCommands(String call) {
        for (Undoable command : this.store.undoHistory()) {
            if (this.labels.containsKey(command)) {
                return; // New commands are always on top
            }
            this.labels.put(command, call);
        }
    }

    private Map<String, Tier> tiers() {
        Map<String, Tier> tiers = new TreeMap<>();
        for (URI uri : this.store.urisInRamByLastUse()) {
            tiers.put(name(uri), Tier.RAM);
        }
        for (URI uri : this.store.urisOnDisk()) {
            tiers.put(name(uri), Tier.DISK);
        }
        return tiers;
    }

    /**
     * What changed between two sets of tiers. Evictions and reloads follow the order of the disk calls
     * made during this call, because that's the order the engine really did them in: a search can use a
     * document and then evict it, so last use from before the call gets the order wrong.
     */
    private String events(Map<String, Tier> before, Map<String, Tier> after) {
        List<String> evicted = new ArrayList<>();
        List<String> loaded = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        for (Map.Entry<String, Tier> entry : after.entrySet()) {
            Tier old = before.get(entry.getKey());
            if (old == null) {
                added.add("{\"name\":" + Json.quote(entry.getKey()) + ",\"tier\":" + tierName(entry.getValue()) + "}");
            } else if (old == Tier.RAM && entry.getValue() == Tier.DISK) {
                evicted.add(entry.getKey());
            } else if (old == Tier.DISK && entry.getValue() == Tier.RAM) {
                loaded.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Tier> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                deleted.add("{\"name\":" + Json.quote(entry.getKey()) + ",\"tier\":" + tierName(entry.getValue()) + "}");
            }
        }
        sortByLastCall(evicted, CallKind.WRITE);
        sortByLastCall(loaded, CallKind.DELETE);
        return "{\"evicted\":" + Json.stringArray(evicted) + ",\"loaded\":" + Json.stringArray(loaded)
                + ",\"added\":" + Json.array(added) + ",\"deleted\":" + Json.array(deleted) + "}";
    }

    private static String tierName(Tier tier) {
        return tier == Tier.RAM ? "\"ram\"" : "\"disk\"";
    }

    private void sortByLastCall(List<String> names, CallKind kind) {
        Map<String, Integer> order = new HashMap<>();
        List<Call> calls = this.disk.calls();
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).kind == kind) {
                order.put(name(calls.get(i).uri), i);
            }
        }
        names.sort(Comparator.comparingInt((String name) -> order.getOrDefault(name, Integer.MAX_VALUE)).thenComparing(Comparator.naturalOrder()));
    }

    private String state() {
        List<String> ram = new ArrayList<>();
        for (URI uri : this.store.urisInRamByLastUse()) {
            Document doc = this.store.documentInRam(uri);
            ram.add(tile(uri, bytes(doc), new TreeMap<>(withoutNulls(doc.getMetadata()))));
        }
        List<String> onDisk = new ArrayList<>();
        for (URI uri : this.store.urisOnDisk()) {
            Stored stored = this.disk.stored(uri);
            onDisk.add(tile(uri, stored.bytes, stored.metadata));
        }
        URI next = this.store.nextToEvict();
        return "{\"ram\":" + Json.array(ram) + ",\"disk\":" + Json.array(onDisk)
                + ",\"nextToEvict\":" + (next == null ? "null" : Json.quote(name(next)))
                + ",\"ramCount\":" + this.store.documentCountInRam() + ",\"ramBytes\":" + this.store.documentBytesInRam()
                + ",\"maxDocuments\":" + this.maxDocuments + ",\"maxBytes\":" + this.maxBytes
                + ",\"history\":" + history() + "}";
    }

    private static String tile(URI uri, int bytes, Map<String, String> metadata) {
        return "{\"name\":" + Json.quote(name(uri)) + ",\"bytes\":" + bytes + ",\"metadata\":" + Json.stringMap(metadata) + "}";
    }

    private static Map<String, String> withoutNulls(Map<String, String> metadata) {
        Map<String, String> present = new HashMap<>(metadata);
        present.values().removeIf(value -> value == null);
        return present;
    }

    private static int bytes(Document doc) {
        return doc.getDocumentTxt().getBytes(StandardCharsets.UTF_8).length;
    }

    private List<Undoable> visibleHistory() {
        List<Undoable> history = this.store.undoHistory();
        return history.subList(0, history.size() - this.loadedCommands);
    }

    private String history() {
        List<String> entries = new ArrayList<>();
        for (Undoable command : visibleHistory()) {
            entries.add("{\"label\":" + Json.quote(this.labels.get(command)) + ",\"bulk\":" + (command instanceof CommandSet)
                    + ",\"targets\":" + Json.stringArray(targets(command)) + "}");
        }
        return Json.array(entries);
    }

    @SuppressWarnings("unchecked")
    private static List<String> targets(Undoable command) {
        List<String> names = new ArrayList<>();
        if (command instanceof GenericCommand) {
            names.add(name(((GenericCommand<URI>) command).getTarget()));
        } else {
            for (GenericCommand<URI> part : (CommandSet<URI>) command) {
                names.add(name(part.getTarget()));
            }
        }
        Collections.sort(names);
        return names;
    }

    private static String rows(List<Document> docs, ToIntFunction<Document> count) {
        List<String> rows = new ArrayList<>();
        for (Document doc : docs) {
            rows.add(row(doc, count));
        }
        return Json.array(rows);
    }

    private static String row(Document doc, ToIntFunction<Document> count) {
        return "{\"name\":" + Json.quote(name(doc.getKey()))
                + (count == null ? "" : ",\"count\":" + count.applyAsInt(doc))
                + ",\"text\":" + Json.quote(doc.getDocumentTxt())
                + ",\"metadata\":" + Json.stringMap(new TreeMap<>(withoutNulls(doc.getMetadata()))) + "}";
    }

    private static String sortedNames(Set<URI> uris) {
        List<String> names = new ArrayList<>();
        for (URI uri : uris) {
            names.add(name(uri));
        }
        Collections.sort(names);
        return Json.stringArray(names);
    }
}
