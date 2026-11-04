package edu.yu.cs.com1320.project.demo;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The disk tier for the browser, where there are no files. Like DocumentPersistenceManager, reading a
 * document always builds a new object. The JSON is only for showing a stored document to the visitor:
 * it holds the same fields as the file DocumentPersistenceManager would write, with its keys in sorted order.
 */
final class BrowserPersistenceManager implements PersistenceManager<URI, Document> {

    enum CallKind {
        WRITE, READ, DELETE
    }

    static final class Call {
        final CallKind kind;
        final URI uri;

        private Call(CallKind kind, URI uri) {
            this.kind = kind;
            this.uri = uri;
        }
    }

    static final class Stored {
        final String text;
        final String binaryBase64;
        final int bytes;
        final Map<String, Integer> wordMap;
        final TreeMap<String, String> metadata;
        final String json;

        private Stored(URI uri, Document doc) {
            this.text = doc.getDocumentTxt();
            this.binaryBase64 = this.text == null ? Base64.getEncoder().encodeToString(doc.getDocumentBinaryData()) : null;
            this.bytes = this.text != null ? this.text.getBytes(StandardCharsets.UTF_8).length : doc.getDocumentBinaryData().length;
            this.wordMap = new TreeMap<>(doc.getWordMap());
            this.metadata = new TreeMap<>();
            // Gson leaves out null values, so a real disk round trip drops them too
            for (Map.Entry<String, String> entry : doc.getMetadata().entrySet()) {
                if (entry.getValue() != null) {
                    this.metadata.put(entry.getKey(), entry.getValue());
                }
            }
            this.json = toJson(uri);
        }

        private String toJson(URI uri) {
            StringBuilder out = new StringBuilder("{\n  \"uri\": ").append(gsonQuote(uri.toString())).append(",\n");
            if (this.text != null) {
                out.append("  \"txt\": ").append(gsonQuote(this.text)).append(",\n");
            } else {
                out.append("  \"binaryData\": ").append(gsonQuote(this.binaryBase64)).append(",\n");
            }
            out.append("  \"wordMap\": ");
            appendObject(out, this.wordMap);
            out.append(",\n  \"metadata\": ");
            appendObject(out, this.metadata);
            return out.append("\n}").toString();
        }

        private static void appendObject(StringBuilder out, Map<String, ?> values) {
            if (values.isEmpty()) {
                out.append("{}");
                return;
            }
            out.append("{\n");
            for (Iterator<? extends Map.Entry<String, ?>> it = values.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, ?> entry = it.next();
                Object value = entry.getValue();
                out.append("    ").append(gsonQuote(entry.getKey())).append(": ");
                out.append(value instanceof String ? gsonQuote((String) value) : String.valueOf(value));
                out.append(it.hasNext() ? ",\n" : "\n");
            }
            out.append("  }");
        }

        /** Gson also escapes these five by default, so a stored document reads the same as a real file. */
        private static String gsonQuote(String text) {
            String quoted = Json.quote(text);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < quoted.length(); i++) {
                char c = quoted.charAt(i);
                if (c == '<' || c == '>' || c == '&' || c == '=' || c == '\'') {
                    out.append(Json.unicodeEscape(c));
                } else {
                    out.append(c);
                }
            }
            return out.toString();
        }
    }

    private final Map<URI, Stored> stored = new HashMap<>();
    private final List<Call> calls = new ArrayList<>();

    @Override
    public void serialize(URI uri, Document val) {
        if (uri == null || val == null) {
            throw new IllegalArgumentException("URI and Document cannot be null");
        }
        this.calls.add(new Call(CallKind.WRITE, uri));
        this.stored.put(uri, new Stored(uri, val));
    }

    @Override
    public Document deserialize(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        this.calls.add(new Call(CallKind.READ, uri));
        Stored saved = this.stored.get(uri);
        if (saved == null) {
            return null;
        }
        DocumentImpl doc = saved.text != null
                ? new DocumentImpl(uri, saved.text, saved.wordMap)
                : new DocumentImpl(uri, Base64.getDecoder().decode(saved.binaryBase64));
        for (Map.Entry<String, String> entry : saved.metadata.entrySet()) {
            doc.setMetadataValue(entry.getKey(), entry.getValue());
        }
        return doc;
    }

    @Override
    public boolean delete(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        this.calls.add(new Call(CallKind.DELETE, uri));
        return this.stored.remove(uri) != null;
    }

    /**
     * What's stored for this URI, or null. This is for display: it isn't a read by the engine, so it
     * doesn't appear in calls().
     */
    Stored stored(URI uri) {
        return this.stored.get(uri);
    }

    List<Call> calls() {
        return this.calls;
    }

    void clearCalls() {
        this.calls.clear();
    }
}
