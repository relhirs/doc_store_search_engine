package edu.yu.cs.com1320.project.stage6;

import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyViewsTest {

    private static final URI A = URI.create("http://views/a");
    private static final URI B = URI.create("http://views/b");
    private static final URI C = URI.create("http://views/c");

    @TempDir
    File dir;

    private DocumentStoreImpl store;

    @BeforeEach
    void setUp() throws IOException {
        store = new DocumentStoreImpl(dir);
        put(A, "alpha shared");
        put(B, "beta shared");
        put(C, "gamma shared");
    }

    private void put(URI uri, String text) throws IOException {
        store.put(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), uri, DocumentFormat.TXT);
    }

    @Test
    void urisInRamByLastUse_FollowsUse() {
        assertEquals(List.of(A, B, C), store.urisInRamByLastUse());
        store.get(A);
        assertEquals(List.of(B, C, A), store.urisInRamByLastUse());
    }

    @Test
    void tierViews_SplitDocumentsBetweenRamAndDisk() {
        store.setMaxDocumentCount(1);
        assertEquals(List.of(C), store.urisInRamByLastUse());
        assertEquals(List.of(A, B), store.urisOnDisk());
        assertEquals(1, store.documentCountInRam());
        assertEquals("gamma shared".length(), store.documentBytesInRam());
    }

    @Test
    void documentInRam_LeavesADiskDocumentOnDisk() {
        store.setMaxDocumentCount(2);
        assertNull(store.documentInRam(A));
        assertEquals(List.of(A), store.urisOnDisk());
        assertNotNull(store.documentInRam(B));
    }

    @Test
    void views_DoNotCountAsUse() {
        store.urisInRamByLastUse();
        store.documentInRam(A);
        store.nextToEvict();
        store.undoHistory();
        store.setMaxDocumentCount(2);
        assertEquals(List.of(A), store.urisOnDisk(), "A was still the least recently used");
    }

    @Test
    void nextToEvict_IsTheDocumentEvictedNext() throws IOException {
        store.search("shared"); // all three now share a last use time
        URI next = store.nextToEvict();
        store.setMaxDocumentCount(2);
        assertEquals(List.of(next), store.urisOnDisk());
    }

    @Test
    void nextToEvict_IsNullWhenRamIsEmpty() {
        assertNull(new DocumentStoreImpl(dir).nextToEvict());
    }

    @Test
    void wordIndexViews_SeeDocumentsOnDiskWithoutLoadingThem() throws IOException {
        store.setMaxDocumentCount(1);
        List<URI> onDisk = store.urisOnDisk();
        assertEquals(3, store.documentsWithWord("shared"));
        assertEquals(1, store.documentsWithWord("alpha"));
        assertEquals(0, store.documentsWithWord("share"));
        assertEquals(List.of('h'), store.nextIndexCharacters("s"));
        assertEquals(onDisk, store.urisOnDisk());
    }

    @Test
    void undoHistory_ShowsBulkDeletesAsOneEntry() throws IOException {
        assertEquals(3, store.undoHistory().size());
        store.deleteAll("shared");
        List<Undoable> history = store.undoHistory();
        assertEquals(4, history.size());
        assertInstanceOf(CommandSet.class, history.get(0));
        assertEquals(3, ((CommandSet<?>) history.get(0)).size());
        assertEquals(C, ((GenericCommand<?>) history.get(1)).getTarget());
        assertEquals(A, ((GenericCommand<?>) history.get(3)).getTarget());

        store.undo();
        assertEquals(3, store.search("shared").size());
        assertEquals(3, store.undoHistory().size());
    }
}
