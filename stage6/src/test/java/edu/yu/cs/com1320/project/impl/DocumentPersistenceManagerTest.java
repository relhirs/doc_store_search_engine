package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.impl.DocumentImpl;
import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentPersistenceManagerTest {

    private DocumentPersistenceManager pm;
    private File baseDir;

    @BeforeEach
    public void setUp() {
        baseDir = new File(System.getProperty("user.dir"), "test_storage");
        baseDir.mkdir();
        pm = new DocumentPersistenceManager(baseDir);
    }

    @AfterEach
    public void tearDown() {
        deleteDirectory(baseDir);
    }

    private void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        file.delete();
    }

    @Test
    public void testSerializeAndDeserializeTextDoc() throws Exception {
        URI uri = new URI("http://www.yu.edu/documents/doc1");
        String text = "This is a simple text document for testing.";
        Document doc = new DocumentImpl(uri, text, null);

        
        pm.serialize(uri, doc);

        File expectedFile = new File(baseDir, "www.yu.edu/documents/doc1.json");
        assertTrue(expectedFile.exists(), "JSON file should be created at the correct path.");

        Document deserializedDoc = pm.deserialize(uri);
        
        assertNotNull(deserializedDoc, "Deserialized document should not be null.");
        assertEquals(doc.getKey(), deserializedDoc.getKey(), "URIs should match.");
        assertEquals(doc.getDocumentTxt(), deserializedDoc.getDocumentTxt(), "Text content should match.");
    }

    @Test
    public void testSerializeAndDeserializeByteDoc() throws Exception {
        URI uri = new URI("http://www.yu.edu/images/img1");
        byte[] data = "fake byte data".getBytes();
        Document doc = new DocumentImpl(uri, data);

        pm.serialize(uri, doc);
        Document deserializedDoc = pm.deserialize(uri);

        assertNotNull(deserializedDoc);
        assertArrayEquals(doc.getDocumentBinaryData(), deserializedDoc.getDocumentBinaryData(), "Byte arrays should match exactly after Base64 decoding.");
    }

    @Test
    public void testDeleteFile() throws Exception {
        URI uri = new URI("http://www.yu.edu/delete/me");
        Document doc = new DocumentImpl(uri, "Delete me please", null);

        pm.serialize(uri, doc);
        File expectedFile = new File(baseDir, "www.yu.edu/delete/me.json");
        assertTrue(expectedFile.exists(), "File should exist before deletion.");

        boolean deleted = pm.delete(uri);
        assertTrue(deleted, "Delete method should return true on success.");
        assertFalse(expectedFile.exists(), "File should be removed from disk.");
    }

    @Test
    public void testDeleteNonExistentFile() throws Exception {
        URI uri = new URI("http://www.yu.edu/does/not/exist");
        boolean deleted = pm.delete(uri);
        assertFalse(deleted, "Deleting a non-existent file should safely return false.");
    }

    @Test
    public void testHttpAndHttpsDocsDoNotShareAFile() throws Exception {
        URI http = new URI("http://www.yu.edu/same/path");
        URI https = new URI("https://www.yu.edu/same/path");
        pm.serialize(http, new DocumentImpl(http, "plain", null));
        pm.serialize(https, new DocumentImpl(https, "secure", null));

        assertEquals("plain", pm.deserialize(http).getDocumentTxt());
        assertEquals("secure", pm.deserialize(https).getDocumentTxt());
    }

    @Test
    public void testParentSegmentsStayInsideBaseDir() throws Exception {
        URI uri = new URI("http://www.yu.edu/../../../escaped");
        pm.serialize(uri, new DocumentImpl(uri, "contained", null));

        assertFalse(new File(baseDir.getParentFile().getParentFile(), "escaped.json").exists(), "File must not leave the base directory.");
        assertEquals("contained", pm.deserialize(uri).getDocumentTxt());
        assertTrue(pm.delete(uri));
    }

    @Test
    public void testNonAsciiTextSurvivesDisk() throws Exception {
        URI uri = new URI("http://www.yu.edu/accents");
        String text = "caf\u00e9 \u65e5\u672c\u8a9e";
        pm.serialize(uri, new DocumentImpl(uri, text, null));

        assertEquals(text, pm.deserialize(uri).getDocumentTxt());
    }
}
