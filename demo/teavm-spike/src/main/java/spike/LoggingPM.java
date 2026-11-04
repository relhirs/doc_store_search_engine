package spike;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import java.io.IOException;
import java.net.URI;

/** Writes every persistence call to the trace, in order. That order is the eviction order. */
final class LoggingPM implements PersistenceManager<URI, Document> {
    private final PersistenceManager<URI, Document> inner;
    private final StringBuilder out;

    LoggingPM(PersistenceManager<URI, Document> inner, StringBuilder out) {
        this.inner = inner;
        this.out = out;
    }

    @Override
    public void serialize(URI key, Document val) throws IOException {
        out.append("  disk write ").append(key).append('\n');
        inner.serialize(key, val);
    }

    @Override
    public Document deserialize(URI key) throws IOException {
        Document doc = inner.deserialize(key);
        out.append("  disk read ").append(key).append(doc == null ? " missing" : " found").append('\n');
        return doc;
    }

    @Override
    public boolean delete(URI key) throws IOException {
        boolean deleted = inner.delete(key);
        out.append("  disk delete ").append(key).append(' ').append(deleted).append('\n');
        return deleted;
    }
}
