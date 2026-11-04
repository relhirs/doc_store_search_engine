package spike;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in for the disk. Mirrors DocumentPersistenceManager: deserialize always builds a new
 * DocumentImpl, the same way a real read from disk does, so object identity behaves the same.
 */
final class MapPM implements PersistenceManager<URI, Document> {
    private static final class Saved {
        String uri;
        String txt;
        String binaryBase64;
        HashMap<String, Integer> wordMap;
        HashMap<String, String> metadata;
    }

    private final Map<URI, Saved> saved = new HashMap<>();

    @Override
    public void serialize(URI uri, Document val) {
        if (uri == null || val == null) {
            throw new IllegalArgumentException("URI and Document cannot be null");
        }
        Saved s = new Saved();
        s.uri = val.getKey().toString();
        if (val.getDocumentTxt() != null) {
            s.txt = val.getDocumentTxt();
        } else if (val.getDocumentBinaryData() != null) {
            s.binaryBase64 = Base64.getEncoder().encodeToString(val.getDocumentBinaryData());
        }
        s.wordMap = new HashMap<>(val.getWordMap());
        // Gson leaves out null values, so a real disk round trip drops them too
        s.metadata = new HashMap<>();
        for (Map.Entry<String, String> entry : val.getMetadata().entrySet()) {
            if (entry.getValue() != null) {
                s.metadata.put(entry.getKey(), entry.getValue());
            }
        }
        saved.put(uri, s);
    }

    @Override
    public Document deserialize(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        Saved s = saved.get(uri);
        if (s == null) {
            return null;
        }
        URI key = URI.create(s.uri);
        DocumentImpl doc = s.txt != null
                ? new DocumentImpl(key, s.txt, s.wordMap)
                : new DocumentImpl(key, Base64.getDecoder().decode(s.binaryBase64));
        for (Map.Entry<String, String> entry : s.metadata.entrySet()) {
            doc.setMetadataValue(entry.getKey(), entry.getValue());
        }
        return doc;
    }

    @Override
    public boolean delete(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        return saved.remove(uri) != null;
    }
}
