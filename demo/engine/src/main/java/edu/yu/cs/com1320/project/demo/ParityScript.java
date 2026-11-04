package edu.yu.cs.com1320.project.demo;

/**
 * One fixed run of the engine that records every response. verify.sh runs it on the JVM and under
 * TeaVM and compares the two byte for byte.
 */
final class ParityScript {

    private static final String[] WORDS = {
        "tree", "trees", "trie", "tries", "treap", "traversal", "heap", "heaps", "hash", "hashing",
        "disk", "disks", "memory", "merge", "stack", "stacks", "search", "seek", "sort", "split",
        "node", "nodes", "key", "keys", "index", "indexes", "cache", "cold", "page", "pages"
    };
    private static final String[] TOPICS = {"trees", "heaps", "storage", "history"};
    private static final String[] AWKWARD = {
        "café déjà-vu naïve", "日本語 mixed with ascii", "emoji 😀 split😀word", "lone \uD83D surrogate", "tabs\tand\nnewlines\r\nhere",
        "quotes \" and \\ slashes", "<tag> & 'single' = ok", "control \u0001\u001F chars", "zero​width", "  separators  "
    };
    private static final String[] AWKWARD_QUERIES = {"日本", "😀", "\uD83D", "é", "", " ", "a b", "ÿ", "Ā", "￿", "\u0000", "\"", "\\"};

    private final StringBuilder out = new StringBuilder();
    private long seed;

    private ParityScript() {
    }

    static String run() {
        ParityScript script = new ParityScript();
        script.awkwardInput();
        for (int round = 1; round <= 12; round++) {
            script.randomized(round);
        }
        return script.out.toString();
    }

    private void record(String label, String response) {
        this.out.append("> ").append(Json.quote(label)).append('\n').append(response).append('\n');
    }

    private void awkwardInput() {
        Engine engine = new Engine();
        for (int i = 0; i < AWKWARD.length; i++) {
            record("put", engine.putDocument("edge-" + i, AWKWARD[i]));
            record("tag", engine.tagDocument("edge-" + i, "note", AWKWARD[i]));
        }
        record("limit", engine.setMaxDocuments(2));
        for (int i = 0; i < AWKWARD.length; i++) {
            record("disk json", engine.diskJson("edge-" + i));
            record("get", engine.getDocument("edge-" + i));
        }
        for (String query : AWKWARD_QUERIES) {
            record("index", engine.indexView(query));
            record("search", engine.search(query, ""));
            record("prefix", engine.searchPrefix(query, ""));
            record("delete", engine.deleteAll(query, ""));
            record("delete prefix", engine.deleteAllPrefix(query, ""));
        }
        record("bad name", engine.putDocument("Not Valid", "text"));
        record("no name", engine.getDocument(""));
        record("blank text", engine.putDocument("blank", "   "));
        record("long word", engine.putDocument("long", "a".repeat(201)));
        record("longest word", engine.putDocument("longest", "a".repeat(200)));
        record("long text", engine.putDocument("long", "word ".repeat(4001)));
        record("bad filter", engine.search("tree", "no tab here"));
        record("bad limit", engine.setMaxDocuments(0));
        record("low byte limit", engine.setMaxBytes(1));
        record("bad disk name", engine.diskJson("Not Valid"));
        record("missing disk doc", engine.diskJson("nothing"));
        record("undo missing", engine.undoDocument("nothing"));
    }

    private int next(int bound) {
        this.seed = this.seed * 6364136223846793005L + 1442695040888963407L;
        return (int) ((this.seed >>> 33) % bound);
    }

    private String word() {
        return WORDS[next(WORDS.length)];
    }

    private String prefix() {
        String word = word();
        return word.substring(0, 1 + next(word.length()));
    }

    private String filter() {
        return "topic\t" + TOPICS[next(TOPICS.length)];
    }

    private void randomized(int round) {
        this.seed = round * 2654435761L;
        Engine engine = new Engine();
        for (int i = 0; i < 20; i++) {
            record("load", engine.putDocument("doc-" + i, text()));
            record("load tag", engine.tagDocument("doc-" + i, "topic", TOPICS[i % TOPICS.length]));
        }
        record("loaded", engine.finishLoading());
        for (int i = 0; i < 300; i++) {
            step(engine);
        }
    }

    private String text() {
        StringBuilder text = new StringBuilder();
        int words = 3 + next(18);
        for (int i = 0; i < words; i++) {
            text.append(word()).append(next(5) == 0 ? ", " : " ");
        }
        return text.toString();
    }

    private void step(Engine engine) {
        String name = "doc-" + next(26);
        switch (next(18)) {
            case 0, 1, 2 -> record("put", engine.putDocument(name, text()));
            case 3, 4 -> record("get", engine.getDocument(name));
            case 5 -> record("delete", engine.deleteDocument(name));
            case 6 -> record("undo", engine.undoLast());
            case 7 -> record("undo doc", engine.undoDocument(name));
            case 8 -> record("search", engine.search(word(), ""));
            case 9 -> {
                String prefix = prefix();
                record("index", engine.indexView(prefix));
                record("prefix", engine.searchPrefix(prefix, ""));
            }
            case 10 -> record("search filter", engine.search(word(), filter()));
            case 11 -> record("prefix filter", engine.searchPrefix(prefix(), filter()));
            case 12 -> record("filter", engine.search("", filter()));
            case 13 -> record("delete all", engine.deleteAll(word(), ""));
            case 14 -> record("delete prefix", engine.deleteAllPrefix(prefix(), next(2) == 0 ? "" : filter()));
            case 15 -> record("tag", engine.tagDocument(name, "topic", TOPICS[next(TOPICS.length)]));
            case 16 -> record("limit", engine.setMaxDocuments(1 + next(24)));
            default -> {
                record("byte limit", engine.setMaxBytes(120 + next(2000)));
                record("disk json", engine.diskJson(name));
            }
        }
    }
}
