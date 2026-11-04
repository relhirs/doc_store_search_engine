package spike;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.teavm.jso.JSExport;

/** Called straight from JavaScript, outside any TeaVM main thread, the way the real demo will call it. */
public class Bridge {
    private static DocumentStoreImpl store;

    @JSExport
    public static String runScript() {
        return Script.run(new MapPM());
    }

    @JSExport
    public static String runUriProbe() {
        return Script.uriProbe();
    }

    @JSExport
    public static void reset() {
        store = new DocumentStoreImpl(new MapPM());
    }

    @JSExport
    public static int putText(String uri, String text) throws IOException {
        return store.put(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), URI.create(uri), DocumentFormat.TXT);
    }

    @JSExport
    public static String searchPrefix(String prefix) throws IOException {
        List<Document> docs = store.searchByPrefix(prefix);
        StringBuilder b = new StringBuilder();
        for (Document d : docs) {
            b.append(d.getKey()).append(' ');
        }
        return b.toString().trim();
    }

    @JSExport
    public static void setMaxDocs(int limit) {
        store.setMaxDocumentCount(limit);
    }

    @JSExport
    public static void undo() {
        store.undo();
    }
}
