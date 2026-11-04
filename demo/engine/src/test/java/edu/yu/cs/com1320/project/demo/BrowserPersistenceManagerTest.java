package edu.yu.cs.com1320.project.demo;

import com.google.gson.JsonParser;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BrowserPersistenceManagerTest {

    private static final URI URI_A = URI.create("http://demo.local/a");

    @TempDir
    File baseDir;

    private final BrowserPersistenceManager browser = new BrowserPersistenceManager();

    /** What DocumentPersistenceManager writes for this document. */
    private String realFile(Document doc) throws IOException {
        new DocumentPersistenceManager(this.baseDir).serialize(URI_A, doc);
        return Files.readString(new File(this.baseDir, "demo.local/a.json").toPath(), StandardCharsets.UTF_8);
    }

    private String browserJson(Document doc) {
        this.browser.serialize(URI_A, doc);
        return this.browser.stored(URI_A).json;
    }

    private void assertSameJson(Document doc) throws IOException {
        assertEquals(JsonParser.parseString(realFile(doc)), JsonParser.parseString(browserJson(doc)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain words words", "<b> & 'x' = y", "quotes \" and \\ slashes", "日本語 and 😀", "tabs\tand\nnewlines"})
    void json_MatchesARealFile(String text) throws IOException {
        Document doc = new DocumentImpl(URI_A, text);
        doc.setMetadataValue("topic", "trees & <heaps>");
        doc.setMetadataValue("era", "1970s");
        assertSameJson(doc);
    }

    @Test
    void json_MatchesARealFileWithNoMetadata() throws IOException {
        assertSameJson(new DocumentImpl(URI_A, "one word"));
    }

    @Test
    void json_DropsNullMetadataLikeARealFile() throws IOException {
        Document doc = new DocumentImpl(URI_A, "one word");
        doc.setMetadataValue("gone", null);
        doc.setMetadataValue("kept", "yes");
        assertSameJson(doc);
    }

    @Test
    void json_MatchesARealFileForBinary() throws IOException {
        assertSameJson(new DocumentImpl(URI_A, new byte[] {0, 1, -1, 127, -128}));
    }

    /** Parsing hides how text is escaped, so this compares the raw line. */
    @Test
    void json_EscapesTextTheWayGsonDoes() throws IOException {
        Document doc = new DocumentImpl(URI_A, "<b> & 'x' = \"y\" \\ \n 😀");
        assertEquals(textLine(realFile(doc)), textLine(browserJson(doc)));
    }

    private static String textLine(String json) {
        return json.lines().filter(line -> line.startsWith("  \"txt\"")).findFirst().orElseThrow();
    }

    @Test
    void deserialize_BuildsANewEqualDocument() {
        Document doc = new DocumentImpl(URI_A, "words words here");
        doc.setMetadataValue("topic", "trees");
        this.browser.serialize(URI_A, doc);
        Document first = this.browser.deserialize(URI_A);
        Document second = this.browser.deserialize(URI_A);
        assertNotSame(first, second);
        assertNotSame(doc, first);
        assertEquals(doc, first);
        assertEquals(2, first.wordCount("words"));
        assertEquals("trees", first.getMetadataValue("topic"));
    }

    @Test
    void deserialize_RestoresBinary() {
        byte[] data = {0, 1, -1, 127, -128};
        this.browser.serialize(URI_A, new DocumentImpl(URI_A, data));
        assertArrayEquals(data, this.browser.deserialize(URI_A).getDocumentBinaryData());
    }

    @Test
    void missingDocument_BehavesLikeAMissingFile() {
        assertNull(this.browser.deserialize(URI_A));
        assertFalse(this.browser.delete(URI_A));
    }

    @Test
    void delete_RemovesTheDocument() {
        this.browser.serialize(URI_A, new DocumentImpl(URI_A, "text"));
        assertTrue(this.browser.delete(URI_A));
        assertNull(this.browser.stored(URI_A));
    }

    @Test
    void calls_RecordOrderButDisplayReadsDoNotCount() {
        this.browser.serialize(URI_A, new DocumentImpl(URI_A, "text"));
        this.browser.stored(URI_A);
        this.browser.deserialize(URI_A);
        this.browser.delete(URI_A);
        List<BrowserPersistenceManager.Call> calls = this.browser.calls();
        assertEquals(3, calls.size());
        assertEquals(BrowserPersistenceManager.CallKind.WRITE, calls.get(0).kind);
        assertEquals(BrowserPersistenceManager.CallKind.READ, calls.get(1).kind);
        assertEquals(BrowserPersistenceManager.CallKind.DELETE, calls.get(2).kind);
    }
}
