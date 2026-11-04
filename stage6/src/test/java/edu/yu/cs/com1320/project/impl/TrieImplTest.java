package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.impl.TrieImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TrieImplTest {

    private TrieImpl<String> trie;

    @BeforeEach
    void setUp() {
        trie = new TrieImpl<>();
    }

    // put / get 

    @Test
    void put_SingleValue_IsRetrievable() {
        trie.put("hello", "world");
        assertTrue(trie.get("hello").contains("world"));
    }

    @Test
    void put_MultipleValuesAtSameKey() {
        trie.put("word", "alpha");
        trie.put("word", "beta");
        assertEquals(2, trie.get("word").size());
    }

    @Test
    void put_NullKeyOrValue_IsNoOp() {
        trie.put(null, "value");
        trie.put("key", null);
        assertTrue(trie.get("key").isEmpty());
    }

    @Test
    void get_NullKey_ReturnsEmptySet() {
        assertNotNull(trie.get(null));
        assertTrue(trie.get(null).isEmpty());
    }

    @Test
    void get_MissingKey_ReturnsEmptySet() {
        assertTrue(trie.get("nothere").isEmpty());
    }

    @Test
    void get_ReturnsDefensiveCopy() {
        trie.put("hello", "alpha");
        trie.get("hello").add("tamper");
        assertEquals(1, trie.get("hello").size());
    }

    //getSorted

    @Test
    void getSorted_ReturnsValuesInComparatorOrder() {
        trie.put("word", "banana");
        trie.put("word", "apple");
        trie.put("word", "cherry");
        assertEquals(List.of("apple", "banana", "cherry"),
                trie.getSorted("word", Comparator.naturalOrder()));
    }

    @Test
    void getSorted_MissingKey_ReturnsEmptyList() {
        assertTrue(trie.getSorted("nothere", Comparator.naturalOrder()).isEmpty());
    }

    //deleteAll

    @Test
    void deleteAll_RemovesAllValuesAndReturnsThem() {
        trie.put("word", "alpha");
        trie.put("word", "beta");
        Set<String> deleted = trie.deleteAll("word");
        assertEquals(2, deleted.size());
        assertTrue(trie.get("word").isEmpty());
    }

    @Test
    void deleteAll_MissingKey_ReturnsEmptySet() {
        assertTrue(trie.deleteAll("nothere").isEmpty());
    }

    @Test
    void deleteAll_PrefixKey_DoesNotAffectLongerKey() {
        trie.put("he", "short");
        trie.put("hello", "long");
        trie.deleteAll("he");
        assertTrue(trie.get("he").isEmpty());
        assertFalse(trie.get("hello").isEmpty());
    }

    //delete

    @Test
    void delete_RemovesSpecificValue() {
        trie.put("word", "alpha");
        trie.put("word", "beta");
        assertEquals("alpha", trie.delete("word", "alpha"));
        assertFalse(trie.get("word").contains("alpha"));
        assertTrue(trie.get("word").contains("beta"));
    }

    @Test
    void delete_ValueNotPresent_ReturnsNull() {
        trie.put("word", "alpha");
        assertNull(trie.delete("word", "nothere"));
    }

    @Test
    void delete_LastValue_ClearsNode() {
        trie.put("word", "only");
        trie.delete("word", "only");
        assertTrue(trie.get("word").isEmpty());
    }

    //deleteAllWithPrefix 

    @Test
    void deleteAllWithPrefix_DeletesAllMatchingKeys() {
        trie.put("app", "doc1");
        trie.put("apple", "doc2");
        trie.put("banana", "doc3");
        Set<String> deleted = trie.deleteAllWithPrefix("app");
        assertEquals(2, deleted.size());
        assertTrue(trie.get("app").isEmpty());
        assertTrue(trie.get("apple").isEmpty());
        assertFalse(trie.get("banana").isEmpty());
    }

    @Test
    void deleteAllWithPrefix_NullPrefix_ReturnsEmptySet() {
        trie.put("word", "value");
        assertTrue(trie.deleteAllWithPrefix(null).isEmpty());
        assertFalse(trie.get("word").isEmpty());
    }

    @Test
    void deleteAllWithPrefix_MissingPrefix_ReturnsEmptySet() {
        assertTrue(trie.deleteAllWithPrefix("xyz").isEmpty());
    }

    //getAllWithPrefixSorted 

    @Test
    void getAllWithPrefixSorted_ReturnsAllValuesUnderPrefixSorted() {
        trie.put("app", "banana");
        trie.put("apple", "apple");
        trie.put("apply", "cherry");
        List<String> result = trie.getAllWithPrefixSorted("app", Comparator.naturalOrder());
        assertEquals(List.of("apple", "banana", "cherry"), result);
    }

    @Test
    void getAllWithPrefixSorted_NullPrefix_ReturnsEmptyList() {
        trie.put("word", "value");
        assertTrue(trie.getAllWithPrefixSorted(null, Comparator.naturalOrder()).isEmpty());
    }

    @Test
    void getAllWithPrefixSorted_ExactKeyIsIncluded() {
        trie.put("app", "exactMatch");
        List<String> result = trie.getAllWithPrefixSorted("app", Comparator.naturalOrder());
        assertEquals(1, result.size());
        assertTrue(result.contains("exactMatch"));
    }

    @Test
    void getAllWithPrefixSorted_DoesNotIncludeShorterPrefixMatch() {
        trie.put("application", "doc1");
        trie.put("ape", "doc2"); // shares "ap" not "app"
        List<String> result = trie.getAllWithPrefixSorted("app", Comparator.naturalOrder());
        assertEquals(1, result.size());
        assertFalse(result.contains("doc2"));
    }

    @Test
    void lookups_CharacterOutsideAlphabet_FindNothing() {
        trie.put("hello", "world");
        assertTrue(trie.get("日本語").isEmpty());
        assertTrue(trie.getSorted("日本語", String::compareTo).isEmpty());
        assertTrue(trie.getAllWithPrefixSorted("日本語", String::compareTo).isEmpty());
        assertTrue(trie.deleteAll("日本語").isEmpty());
        assertTrue(trie.deleteAllWithPrefix("日本語").isEmpty());
        assertNull(trie.delete("日本語", "world"));
        assertEquals(1, trie.get("hello").size());
    }

    @Test
    void put_CharacterOutsideAlphabet_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> trie.put("日本語", "world"));
    }

    @Test
    void nextCharacters_ListsTheBranchesInOrder() {
        trie.put("tree", "a");
        trie.put("trie", "a");
        trie.put("top", "a");
        assertEquals(List.of('o', 'r'), trie.nextCharacters("t"));
        assertEquals(List.of('e', 'i'), trie.nextCharacters("tr"));
        assertEquals(List.of('t'), trie.nextCharacters(""));
        assertTrue(trie.nextCharacters("tree").isEmpty());
    }

    @Test
    void nextCharacters_MissingOrUnusablePrefix_IsEmpty() {
        trie.put("tree", "a");
        assertTrue(trie.nextCharacters("x").isEmpty());
        assertTrue(trie.nextCharacters("日本語").isEmpty());
        assertTrue(trie.nextCharacters(null).isEmpty());
    }

    @Test
    void nextCharacters_DropsBranchesOnceTheirWordsAreGone() {
        trie.put("tree", "a");
        trie.put("trie", "a");
        trie.delete("trie", "a");
        assertEquals(List.of('e'), trie.nextCharacters("tr"));
    }

    @Test
    void get_ValuesComeBackInInsertionOrder() {
        List<String> inserted = List.of("zebra", "apple", "mango", "kiwi", "fig", "lime");
        for (String value : inserted) {
            trie.put("word", value);
        }
        assertEquals(inserted, List.copyOf(trie.get("word")));
        assertEquals(inserted, trie.getSorted("word", (a, b) -> 0));
        assertEquals(inserted, trie.getAllWithPrefixSorted("wo", (a, b) -> 0));
    }
}
