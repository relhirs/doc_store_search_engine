package edu.yu.cs.com1320.project.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static edu.yu.cs.com1320.project.demo.Responses.*;
import static org.junit.jupiter.api.Assertions.*;

public class EngineTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        this.engine = new Engine();
    }

    private void load(String... names) {
        for (String name : names) {
            this.engine.putDocument(name, "shared words for " + name.replace("-", " "));
        }
    }

    private static void assertRejected(String response, String expectedError) {
        JsonObject parsed = parse(response);
        assertFalse(parsed.get("ok").getAsBoolean());
        assertTrue(parsed.get("error").getAsString().contains(expectedError), parsed.get("error").getAsString());
        assertNotNull(parsed.get("state"), "a rejected call still reports the state");
    }

    @Test
    void put_ReportsNewThenReplaced() {
        assertEquals("new", parse(this.engine.putDocument("a", "one")).get("result").getAsString());
        assertEquals("replaced", parse(this.engine.putDocument("a", "two")).get("result").getAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Upper", "has space", "dot.dot", "../up", "http://x", "日本"})
    void names_OutsideTheAllowedFormAreRejected(String name) {
        assertRejected(this.engine.putDocument(name, "text"), "lowercase");
        assertEquals(0, state(this.engine.snapshot()).getAsJsonArray("ram").size());
    }

    @Test
    void blankText_IsRejected() {
        assertRejected(this.engine.putDocument("a", "   "), "needs some text");
    }

    @Test
    void aWordTooLongForTheTrie_IsRejectedBeforeTheStore() {
        assertRejected(this.engine.putDocument("a", "short " + "a".repeat(201) + " words"), "A word can be up to 200");
        assertEquals(0, state(this.engine.snapshot()).getAsJsonArray("ram").size());
        assertTrue(parse(this.engine.putDocument("a", "a".repeat(200))).get("ok").getAsBoolean());
    }

    @Test
    void wordsSplitOnPunctuation_CanBeLongTogether() {
        assertTrue(parse(this.engine.putDocument("a", ("a".repeat(150) + "-").repeat(10))).get("ok").getAsBoolean());
    }

    @Test
    void aDocumentTooLargeForTheBrowser_IsRejectedBeforeTheStore() {
        assertRejected(this.engine.putDocument("a", "word ".repeat(4001)), "up to 20000 characters");
        assertTrue(parse(this.engine.putDocument("a", "word ".repeat(4000))).get("ok").getAsBoolean());
    }

    @Test
    void emptySearchInput_NeverReachesTheStore() {
        load("a");
        for (String response : List.of(this.engine.search("", ""), this.engine.searchPrefix("  ", ""),
                this.engine.deleteAll("", ""), this.engine.deleteAllPrefix("", ""))) {
            JsonObject parsed = parse(response);
            assertTrue(parsed.get("call").isJsonNull());
            assertEquals(0, parsed.getAsJsonArray("result").size());
        }
        assertEquals(List.of("a"), ramNames(this.engine.snapshot()));
    }

    @Test
    void indexView_FollowsTheTextUntilTheIndexRunsOut() {
        this.engine.putDocument("a", "tree trie");
        this.engine.putDocument("b", "tree");
        JsonArray levels = parse(this.engine.indexView("trex")).getAsJsonArray("result");
        assertEquals(4, levels.size(), "the root, then t, r and e, but no x");
        assertEquals(List.of("e", "i"), strings(levels.get(2).getAsJsonObject().getAsJsonArray("next")));
        assertEquals("tre", levels.get(3).getAsJsonObject().get("prefix").getAsString());
        assertEquals(2, parse(this.engine.indexView("tree")).getAsJsonArray("result").get(4).getAsJsonObject().get("documents").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"日本", "😀", "Tree"})
    void indexView_OfTextNotInTheIndex_StopsAtTheRoot(String text) {
        this.engine.putDocument("a", "tree");
        assertEquals(1, parse(this.engine.indexView(text)).getAsJsonArray("result").size());
    }

    @Test
    void search_RanksByCountAndReportsIt() {
        this.engine.putDocument("few", "tree");
        this.engine.putDocument("many", "tree tree tree");
        JsonArray rows = parse(this.engine.search("tree", "")).getAsJsonArray("result");
        assertEquals(List.of("many", "few"), names(rows));
        assertEquals(3, rows.get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void prefixSearch_CountsEveryMatchingWord() {
        this.engine.putDocument("a", "tree trie trie other");
        JsonArray rows = parse(this.engine.searchPrefix("tr", "")).getAsJsonArray("result");
        assertEquals(3, rows.get(0).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void filters_NarrowSearchAndWorkAlone() {
        load("a", "b");
        this.engine.tagDocument("a", "topic", "trees");
        this.engine.tagDocument("a", "era", "1970s");
        this.engine.tagDocument("b", "topic", "heaps");
        assertEquals(List.of("a"), names(parse(this.engine.search("shared", "topic\ttrees")).getAsJsonArray("result")));
        assertEquals(List.of("a"), names(parse(this.engine.search("", "topic\ttrees\nera\t1970s")).getAsJsonArray("result")));
        assertEquals(List.of(), names(parse(this.engine.searchPrefix("sh", "topic\ttrees\nera\t1980s")).getAsJsonArray("result")));
    }

    @Test
    void malformedFilters_AreRejectedNotThrown() {
        assertRejected(this.engine.search("tree", "no tab"), "key, tab, value");
        assertRejected(this.engine.deleteAll("tree", "no tab"), "key, tab, value");
    }

    @Test
    void eviction_FollowsTheEngineNotLastUseBeforeTheCall() {
        this.engine.putDocument("c", "shared cold");
        this.engine.putDocument("d", "shared cold");
        this.engine.setMaxDocuments(1);
        this.engine.putDocument("x", "shared shared shared");
        this.engine.setMaxDocuments(2);
        this.engine.putDocument("y", "other");
        // x ranks first, so the search uses it and then fetches c and d from disk. That makes y the
        // older of the two, so y goes first, even though x was the older before the call.
        assertEquals(List.of("x", "y"), ramNames(this.engine.snapshot()));
        JsonObject events = events(this.engine.search("shared", ""));
        assertEquals(2, events.getAsJsonArray("evicted").size());
        assertEquals(List.of("y", "x"), strings(events.getAsJsonArray("evicted")));
    }

    @Test
    void reload_MovesATileAndPushesAnotherOut() {
        load("a", "b", "c");
        this.engine.setMaxDocuments(2);
        JsonObject events = events(this.engine.getDocument("a"));
        assertEquals(List.of("a"), strings(events.getAsJsonArray("loaded")));
        assertEquals(List.of("b"), strings(events.getAsJsonArray("evicted")));
    }

    @Test
    void bulkDelete_ReportsDeletesNotReloads() {
        load("a", "b", "c");
        this.engine.setMaxDocuments(1);
        JsonObject events = events(this.engine.deleteAll("shared", ""));
        assertEquals(0, events.getAsJsonArray("loaded").size());
        assertEquals(List.of("a", "b", "c"), names(events.getAsJsonArray("deleted")));
        assertEquals("disk", events.getAsJsonArray("deleted").get(0).getAsJsonObject().get("tier").getAsString());
    }

    @Test
    void undoingABulkDelete_ReportsEveryDocumentAdded() {
        load("a", "b");
        this.engine.deleteAll("shared", "");
        assertEquals(List.of("a", "b"), names(events(this.engine.undoLast()).getAsJsonArray("added")));
    }

    @Test
    void nextToEvict_NamesTheRealVictim() {
        load("a", "b", "c");
        String next = state(this.engine.search("shared", "")).get("nextToEvict").getAsString();
        assertEquals(List.of(next), strings(events(this.engine.setMaxDocuments(2)).getAsJsonArray("evicted")));
    }

    @Test
    void loading_StaysOutOfTheVisitorsHistory() {
        load("a", "b");
        this.engine.tagDocument("a", "topic", "trees");
        assertEquals(0, state(this.engine.finishLoading()).getAsJsonArray("history").size());
        assertRejected(this.engine.undoLast(), "nothing to undo");
        assertRejected(this.engine.undoDocument("a"), "nothing to undo");
        assertEquals(2, ramNames(this.engine.snapshot()).size());
    }

    @Test
    void undoDocument_ReachesOnlyTheVisitorsActions() {
        load("a", "b");
        this.engine.finishLoading();
        this.engine.deleteDocument("a");
        this.engine.deleteDocument("b");
        assertTrue(parse(this.engine.undoDocument("a")).get("ok").getAsBoolean());
        assertEquals(List.of("a"), ramNames(this.engine.snapshot()));
        JsonArray history = state(this.engine.snapshot()).getAsJsonArray("history");
        assertEquals(1, history.size());
        assertEquals(List.of("b"), strings(history.get(0).getAsJsonObject().getAsJsonArray("targets")));
    }

    @Test
    void labels_StayWithTheirOwnCommands() {
        load("a");
        this.engine.finishLoading();
        this.engine.tagDocument("a", "k", "one");
        this.engine.tagDocument("a", "k", "two");
        JsonArray history = state(this.engine.snapshot()).getAsJsonArray("history");
        assertTrue(history.get(0).getAsJsonObject().get("label").getAsString().contains("two"));
        assertTrue(history.get(1).getAsJsonObject().get("label").getAsString().contains("one"));
    }

    @Test
    void labels_SurviveAPartialUndoOfABulkDelete() {
        load("a", "b", "c");
        this.engine.finishLoading();
        this.engine.deleteAll("shared", "");
        this.engine.undoDocument("b");
        JsonObject entry = state(this.engine.snapshot()).getAsJsonArray("history").get(0).getAsJsonObject();
        assertEquals("store.deleteAll(\"shared\")", entry.get("label").getAsString());
        assertTrue(entry.get("bulk").getAsBoolean());
        assertEquals(List.of("a", "c"), strings(entry.getAsJsonArray("targets")));
    }

    @Test
    void byteLimit_CannotGoBelowTheLargestDocument() {
        this.engine.putDocument("big", "a much longer document than the other one");
        this.engine.putDocument("small", "tiny");
        this.engine.setMaxDocuments(1);
        assertRejected(this.engine.setMaxBytes(10), "largest document");
        assertEquals(-1, state(this.engine.snapshot()).get("maxBytes").getAsInt());
        assertTrue(parse(this.engine.setMaxBytes(41)).get("ok").getAsBoolean());
    }

    @Test
    void diskJson_ShowsOnlyDocumentsOnDisk() {
        load("a", "b");
        this.engine.setMaxDocuments(1);
        assertTrue(parse(this.engine.diskJson("a")).get("result").getAsString().contains("\"uri\": \"http://demo.local/a\""));
        assertTrue(parse(this.engine.diskJson("b")).get("result").isJsonNull());
        assertFalse(parse(this.engine.diskJson("Bad Name")).get("ok").getAsBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"quotes \" and \\ slashes", "tabs\tand\nnewlines\r\n", "control \u0001 chars", "日本語 and 😀 emoji", "<b> & 'x' = y"})
    void awkwardText_SurvivesTheJsonRoundTrip(String text) {
        this.engine.putDocument("a", text);
        assertEquals(text, parse(this.engine.getDocument("a")).getAsJsonObject("result").get("text").getAsString());
    }

    @Test
    void loneSurrogate_StillGivesValidJson() {
        assertEquals("\uD83D", parse("{\"s\":" + Json.quote("\uD83D") + "}").get("s").getAsString());
        assertEquals("\"\\ud83d\"", Json.quote("\uD83D"));
        assertEquals("\"😀\"", Json.quote("😀"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"日本", "😀", "\uD83D", "\u0100", "\uFFFF"})
    void nonLatinSearch_FindsNothingAndDoesNotFail(String query) {
        load("a");
        assertEquals(0, parse(this.engine.search(query, "")).getAsJsonArray("result").size());
        assertEquals(0, parse(this.engine.searchPrefix(query, "")).getAsJsonArray("result").size());
    }
}
