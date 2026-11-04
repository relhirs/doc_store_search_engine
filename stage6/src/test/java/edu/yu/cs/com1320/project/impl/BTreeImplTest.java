package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class BTreeImplTest {

    private BTreeImpl<Integer, String> bTree;
    private DummyPM dummyPM;

    private class DummyPM implements PersistenceManager<Integer, String> {
        Map<Integer, String> diskStorage = new HashMap<>();

        @Override
        public void serialize(Integer key, String val) throws IOException {
            diskStorage.put(key, val);
        }

        @Override
        public String deserialize(Integer key) throws IOException {
            return diskStorage.get(key);
        }

        @Override
        public boolean delete(Integer key) throws IOException {
            return diskStorage.remove(key) != null;
        }
    }

    @BeforeEach
    public void setUp() {
        bTree = new BTreeImpl<>();
        dummyPM = new DummyPM();
        bTree.setPersistenceManager(dummyPM);
    }

    @Test
    public void testBasicPutAndGet() {
        bTree.put(1, "Value1");
        assertEquals("Value1", bTree.get(1), "Should retrieve the exact value inserted.");
    }

    @Test
    public void testPutOverwrite() {
        bTree.put(1, "Value1");
        String oldVal = bTree.put(1, "Value2");
        
        assertEquals("Value1", oldVal, "Put should return the old value when overwriting.");
        assertEquals("Value2", bTree.get(1), "Get should return the newly overwritten value.");
    }

    @Test
    public void testNullKeysThrowExceptions() {
        assertThrows(IllegalArgumentException.class, () -> {
            bTree.put(null, "Value");
        }, "Putting a null key should throw an exception.");

        assertThrows(IllegalArgumentException.class, () -> {
            bTree.get(null);
        }, "Getting a null key should throw an exception.");
    }

    @Test
    public void testMoveToDiskAndRetrieve() throws Exception {
        bTree.put(1, "Value1");
        bTree.moveToDisk(1);

        // Verify it was written to our dummy disk
        assertTrue(dummyPM.diskStorage.containsKey(1), "Value should be serialized to disk.");

        // Retrieve it (should bring it back from disk and delete from disk)
        String retrieved = bTree.get(1);
        assertEquals("Value1", retrieved, "Should successfully deserialize from disk.");
        assertFalse(dummyPM.diskStorage.containsKey(1), "Should delete from disk after bringing back to RAM.");
    }

    @Test
    public void testNodeSplitting() {
        // The MAX capacity is 4, so inserting 10 items forces multiple node splits
        for (int i = 1; i <= 10; i++) {
            bTree.put(i, "Val" + i);
        }

        for (int i = 1; i <= 10; i++) {
            assertEquals("Val" + i, bTree.get(i), "Should retrieve value " + i + " after splits.");
        }
    }

    @Test
    public void testDeleteViaPutNull() {
        bTree.put(1, "Value1");
        bTree.put(1, null);
        assertNull(bTree.get(1), "Putting a null value should effectively delete the entry.");
    }

    @Test
    public void testDescendingInserts() {
        for (int i = 100; i >= 1; i--) {
            bTree.put(i, "Val" + i);
        }

        for (int i = 1; i <= 100; i++) {
            assertEquals("Val" + i, bTree.get(i), "Should retrieve value " + i + " after descending inserts.");
        }
    }

    @Test
    public void testShuffledInsertsKeepEveryKeyAfterEveryPut() {
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            keys.add(i);
        }
        Random random = new Random(1320);
        for (int run = 0; run < 200; run++) {
            Collections.shuffle(keys, random);
            BTreeImpl<Integer, String> tree = new BTreeImpl<>();
            tree.setPersistenceManager(dummyPM);
            for (int i = 0; i < keys.size(); i++) {
                tree.put(keys.get(i), "Val" + keys.get(i));
                for (int j = 0; j <= i; j++) {
                    assertEquals("Val" + keys.get(j), tree.get(keys.get(j)), "Run " + run + " lost key " + keys.get(j));
                }
            }
        }
    }

    @Test
    public void testDeletedKeyWithoutPersistenceManager() {
        BTreeImpl<Integer, String> tree = new BTreeImpl<>();
        tree.put(1, "Value1");
        tree.put(1, null);
        assertNull(tree.get(1), "A deleted key should read as null even with no persistence manager.");
    }

    @Test
    public void testDiskReadFailureIsNotReportedAsMissing() throws Exception {
        bTree.put(1, "Value1");
        bTree.moveToDisk(1);
        bTree.setPersistenceManager(new DummyPM() {
            @Override
            public String deserialize(Integer key) throws IOException {
                throw new IOException("disk failed");
            }
        });
        assertThrows(UncheckedIOException.class, () -> bTree.get(1), "A failed read must not look like a missing key.");
    }
}
