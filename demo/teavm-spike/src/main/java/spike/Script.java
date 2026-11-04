package spike;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** One deterministic run of the engine. Never prints a timestamp, so traces can be compared byte for byte. */
final class Script {
    private static final String[] WORDS = {
        "tree", "trees", "trie", "tries", "treap", "traversal", "heap", "heaps", "hash", "hashing",
        "disk", "disks", "memory", "merge", "stack", "stacks", "search", "seek", "sort", "split",
        "node", "nodes", "key", "keys", "index", "indexes", "cache", "cold", "page", "pages"
    };
    private static final String[] TOPICS = {"trees", "heaps", "storage", "history"};

    private interface Op {
        String call() throws Exception;
    }

    private final StringBuilder out = new StringBuilder();
    private final PersistenceManager<URI, Document> disk;
    private DocumentStoreImpl store;
    private long seed;

    private Script(PersistenceManager<URI, Document> disk) {
        this.disk = disk;
    }

    static String run(PersistenceManager<URI, Document> disk) {
        Script s = new Script(disk);
        s.uriSemantics();
        s.scripted();
        s.textEdges();
        s.binaryAndBytes();
        for (int i = 1; i <= 40; i++) {
            s.randomized(i);
        }
        return s.out.toString();
    }

    private void fresh() {
        store = new DocumentStoreImpl(new LoggingPM(disk, out));
    }

    private void step(String label, Op op) {
        out.append("> ").append(label).append('\n');
        try {
            String result = op.call();
            out.append("= ").append(result).append('\n');
        } catch (Exception e) {
            out.append("! ").append(e.getClass().getName()).append('\n');
        }
    }

    private static URI u(String s) {
        return URI.create(s);
    }

    private String put(String uri, String text) throws Exception {
        return kind(store.put(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), u(uri), DocumentFormat.TXT));
    }

    private static String kind(int previousHash) {
        return previousHash == 0 ? "new" : "replaced";
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            b.append(Integer.toHexString(s.charAt(i))).append(' ');
        }
        return b.toString();
    }

    private static String ranked(List<Document> docs, String word) {
        StringBuilder b = new StringBuilder();
        for (Document d : docs) {
            b.append(d.getKey());
            if (word != null) {
                b.append(':').append(d.wordCount(word));
            }
            b.append(' ');
        }
        return b.toString();
    }

    private static String sorted(Set<URI> uris) {
        List<String> names = new ArrayList<>();
        for (URI uri : uris) {
            names.add(uri.toString());
        }
        Collections.sort(names);
        return names.toString();
    }

    private static String show(Document d) {
        if (d == null) {
            return "null";
        }
        String body = d.getDocumentTxt() != null ? d.getDocumentTxt() : Arrays.toString(d.getDocumentBinaryData());
        return d.getKey() + " " + body + " words=" + new TreeMap<>(d.getWordMap()) + " meta=" + new TreeMap<>(d.getMetadata());
    }

    private static Map<String, String> meta(String key, String value) {
        Map<String, String> m = new HashMap<>();
        m.put(key, value);
        return m;
    }

    static String uriProbe() {
        Script s = new Script(null);
        s.compareUris(new String[] {
            "http://site.com/a", "http://Site.com/a", "HTTP://site.com/a", "http://site.com/%2f", "http://site.com/%2F"
        });
        s.sortProbe();
        return s.out.toString();
    }

    /** Records the order a sort asks about elements in. The sorted result is compared separately. */
    private void sortProbe() {
        List<Integer> values = new ArrayList<>(Arrays.asList(7, 3, 9, 1, 8, 2, 6, 4, 5, 0));
        StringBuilder calls = new StringBuilder();
        values.sort((a, b) -> {
            calls.append(a).append('/').append(b).append(' ');
            return Integer.compare(a, b);
        });
        out.append("sort result ").append(values).append('\n');
        out.append("sort calls ").append(calls).append('\n');
    }

    /** java.net.URI is the B-Tree key, so TeaVM's version has to agree with the JDK's. */
    private void uriSemantics() {
        out.append("# uri semantics\n");
        compareUris(new String[] {
            "http://site.com/a", "http://site.com/B", "https://site.com/a", "http://site.com/a?x=1", "http://site.com/a#frag",
            "http://site.com:8080/a", "http://site.com/%2f", "mailto:ada@site.com", "urn:isbn:123", "file:///tmp/x",
            "relative/path", "/abs/path", "http://site.com", "http://site.com/", "http://site.com/a/b/../c"
        });
        for (String bad : new String[] {"http://site.com/a b", "http://[bad", "", ":", "http://site.com/日本"}) {
            step("create " + bad, () -> u(bad).toString());
        }
    }

    private void compareUris(String[] uris) {
        List<URI> list = new ArrayList<>();
        for (String s : uris) {
            list.add(u(s));
        }
        Collections.sort(list);
        out.append(list).append('\n');
        for (String a : uris) {
            for (String b : uris) {
                URI x = u(a);
                URI y = u(b);
                int c = Integer.signum(x.compareTo(y));
                boolean e = x.equals(y);
                if (e && x.hashCode() != y.hashCode()) {
                    out.append("HASH MISMATCH ").append(a).append(' ').append(b).append('\n');
                }
                out.append(c).append(e ? 'e' : 'n');
            }
            out.append('\n');
        }
    }

    private void scripted() {
        out.append("# scripted\n");
        fresh();
        for (int i = 23; i >= 0; i--) {
            int n = i;
            String text = WORDS[n % 30] + " " + WORDS[(n * 7) % 30] + " " + WORDS[(n * 11 + 3) % 30] + " " + WORDS[n % 30] + " tree";
            String uri = "http://demo/doc" + (n < 10 ? "0" : "") + n;
            step("put " + uri, () -> "" + put(uri, text));
            step("tag " + uri, () -> store.setMetadata(u(uri), "topic", TOPICS[n % 4]));
        }
        step("search tree", () -> ranked(store.search("tree"), "tree"));
        step("prefix tr", () -> ranked(store.searchByPrefix("tr"), null));
        step("prefix t", () -> ranked(store.searchByPrefix("t"), "tree"));
        step("limit 16", () -> {
            store.setMaxDocumentCount(16);
            return "ok";
        });
        step("get evicted doc23", () -> show(store.get(u("http://demo/doc23"))));
        step("get evicted doc22", () -> show(store.get(u("http://demo/doc22"))));
        step("meta topic trees", () -> ranked(store.searchByMetadata(meta("topic", "trees")), null));
        step("kw+meta", () -> ranked(store.searchByKeywordAndMetadata("tree", meta("topic", "heaps")), "tree"));
        step("prefix+meta", () -> ranked(store.searchByPrefixAndMetadata("tr", meta("topic", "storage")), null));
        step("limit 4", () -> {
            store.setMaxDocumentCount(4);
            return "ok";
        });
        step("undo latest", () -> {
            store.undo();
            return "ok";
        });
        step("undo put doc23", () -> {
            store.undo(u("http://demo/doc23"));
            return "ok";
        });
        step("get doc23", () -> show(store.get(u("http://demo/doc23"))));
        step("get doc10", () -> show(store.get(u("http://demo/doc10"))));
        step("delete all prefix tr", () -> sorted(store.deleteAllWithPrefix("tr")));
        step("search after delete", () -> ranked(store.searchByPrefix("tr"), null));
        step("undo bulk", () -> {
            store.undo();
            return "ok";
        });
        step("search after undo", () -> ranked(store.searchByPrefix("tr"), null));
        step("delete all tree", () -> sorted(store.deleteAll("tree")));
        step("undo one of bulk", () -> {
            store.undo(u("http://demo/doc05"));
            return "ok";
        });
        step("search after partial", () -> ranked(store.search("tree"), "tree"));
        step("undo rest", () -> {
            store.undo();
            return "ok";
        });
        step("delete meta", () -> sorted(store.deleteAllWithMetadata(meta("topic", "history"))));
        step("delete kw+meta", () -> sorted(store.deleteAllWithKeywordAndMetadata("tree", meta("topic", "trees"))));
        step("delete prefix+meta", () -> sorted(store.deleteAllWithPrefixAndMetadata("t", meta("topic", "heaps"))));
        step("put null deletes", () -> kind(store.put(null, u("http://demo/doc00"), DocumentFormat.TXT)));
        step("get missing", () -> show(store.get(u("http://demo/nothing"))));
        step("delete missing", () -> "" + store.delete(u("http://demo/nothing")));
        step("undo missing", () -> {
            store.undo(u("http://demo/nothing"));
            return "ok";
        });
    }

    private void textEdges() {
        out.append("# text edges\n");
        fresh();
        String[] texts = {
            "  ,,leading punctuation and trailing... ", "café déjà-vu naïve résumé", "a_b c-d e.f g'h 3.14 100%",
            "日本語 のテキスト mixed with ascii", "emoji 😀 split😀word end", "tabs\tand\nnewlines\r\nhere", "UPPER lower MiXeD Upper",
            "ß ẞ ı İ", "zero​width nbsp here", "!!!", "x"
        };
        for (int i = 0; i < texts.length; i++) {
            String text = texts[i];
            String uri = "http://edge/t" + i;
            step("put " + uri, () -> "" + put(uri, text));
            step("show " + uri, () -> show(store.get(u(uri))));
        }
        String[] queries = {"日本", "日本語", "😀", "\uD83D", "é", "caf", "Upper", "upper", "", " ", "a b", "split", "ÿ", "Ā", "￿", "\u0000"};
        for (String q : queries) {
            step("search [" + esc(q) + "]", () -> ranked(store.search(q), null));
            step("prefix [" + esc(q) + "]", () -> ranked(store.searchByPrefix(q), null));
            step("deleteAll [" + esc(q) + "]", () -> sorted(store.deleteAll(q)));
            step("undo", () -> {
                store.undo();
                return "ok";
            });
        }
        step("search null", () -> ranked(store.search(null), null));
        step("prefix null", () -> ranked(store.searchByPrefix(null), null));
        step("meta blank key", () -> store.setMetadata(u("http://edge/t1"), " ", "v"));
        step("meta blank uri", () -> store.setMetadata(u(""), "k", "v"));
        step("put blank", () -> "" + put("http://edge/blank", "   "));
        step("put empty", () -> "" + put("http://edge/empty", ""));
    }

    private void binaryAndBytes() {
        out.append("# binary and bytes\n");
        fresh();
        byte[] data = new byte[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 37);
        }
        URI bin = u("http://bin/one");
        step("put binary", () -> kind(store.put(new ByteArrayInputStream(data), bin, DocumentFormat.BINARY)));
        step("tag binary", () -> store.setMetadata(bin, "kind", "blob"));
        step("put a", () -> "" + put("http://bin/a", "alpha beta alpha"));
        step("put b", () -> "" + put("http://bin/b", "日本語 multi byte text"));
        step("limit bytes 40", () -> {
            store.setMaxDocumentBytes(40);
            return "ok";
        });
        step("get binary", () -> show(store.get(bin)));
        step("get a", () -> show(store.get(u("http://bin/a"))));
        step("get b", () -> show(store.get(u("http://bin/b"))));
        step("meta kind", () -> ranked(store.searchByMetadata(meta("kind", "blob")), null));
        step("too big", () -> "" + put("http://bin/big", "this document is much longer than the forty bytes allowed by the limit"));
        step("limit 0", () -> {
            store.setMaxDocumentBytes(0);
            return "ok";
        });
        step("limit -1", () -> {
            store.setMaxDocumentCount(-1);
            return "ok";
        });
    }

    private int next(int bound) {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return (int) ((seed >>> 33) % bound);
    }

    private String word() {
        return WORDS[next(WORDS.length)];
    }

    private String prefix() {
        String w = word();
        return w.substring(0, 1 + next(w.length()));
    }

    private String uri(int round) {
        return "http://r" + round + "/doc" + next(40);
    }

    private void randomized(int round) {
        out.append("# random ").append(round).append('\n');
        seed = round * 2654435761L;
        fresh();
        for (int i = 0; i < 600; i++) {
            int kind = next(20);
            String uri = uri(round);
            String w = word();
            String p = prefix();
            Map<String, String> tag = meta("topic", TOPICS[next(4)]);
            switch (kind) {
                case 0, 1, 2, 3, 4, 5, 6 -> {
                    StringBuilder text = new StringBuilder();
                    int words = 3 + next(18);
                    for (int j = 0; j < words; j++) {
                        text.append(word()).append(next(5) == 0 ? ", " : " ");
                    }
                    step("put " + uri, () -> "" + put(uri, text.toString()));
                }
                case 7 -> step("get " + uri, () -> show(store.get(u(uri))));
                case 8 -> step("delete " + uri, () -> "" + store.delete(u(uri)));
                case 9 -> step("undo", () -> {
                    store.undo();
                    return "ok";
                });
                case 10 -> step("undo " + uri, () -> {
                    store.undo(u(uri));
                    return "ok";
                });
                case 11 -> step("search " + w, () -> ranked(store.search(w), w));
                case 12 -> step("prefix " + p, () -> ranked(store.searchByPrefix(p), null));
                case 13 -> step("deleteAll " + w, () -> sorted(store.deleteAll(w)));
                case 14 -> step("deleteAllPrefix " + p, () -> sorted(store.deleteAllWithPrefix(p)));
                case 15 -> step("tag " + uri, () -> store.setMetadata(u(uri), "topic", tag.get("topic")));
                case 16 -> step("meta", () -> ranked(store.searchByMetadata(tag), null));
                case 17 -> step("kw+meta " + w, () -> ranked(store.searchByKeywordAndMetadata(w, tag), w));
                case 18 -> {
                    int limit = 1 + next(30);
                    step("limit count " + limit, () -> {
                        store.setMaxDocumentCount(limit);
                        return "ok";
                    });
                }
                default -> {
                    int limit = 200 + next(3000);
                    step("limit bytes " + limit, () -> {
                        store.setMaxDocumentBytes(limit);
                        return "ok";
                    });
                }
            }
        }
    }
}
