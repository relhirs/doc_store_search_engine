package edu.yu.cs.com1320.project.demo;

import org.teavm.jso.JSExport;

/**
 * What JavaScript can call. Every method returns JSON, described on {@link Engine}. Filters are written
 * as one "key\tvalue" pair per line.
 */
public final class DemoEngine {

    private static Engine engine = new Engine(System::nanoTime);

    private DemoEngine() {
    }

    @JSExport
    public static String reset() {
        engine = new Engine(System::nanoTime);
        return engine.snapshot();
    }

    @JSExport
    public static String putDocument(String name, String text) {
        return engine.putDocument(name, text);
    }

    @JSExport
    public static String tagDocument(String name, String key, String value) {
        return engine.tagDocument(name, key, value);
    }

    @JSExport
    public static String finishLoading() {
        return engine.finishLoading();
    }

    @JSExport
    public static String getDocument(String name) {
        return engine.getDocument(name);
    }

    @JSExport
    public static String deleteDocument(String name) {
        return engine.deleteDocument(name);
    }

    @JSExport
    public static String undoLast() {
        return engine.undoLast();
    }

    @JSExport
    public static String undoDocument(String name) {
        return engine.undoDocument(name);
    }

    @JSExport
    public static String search(String keyword, String filters) {
        return engine.search(keyword, filters);
    }

    @JSExport
    public static String searchPrefix(String prefix, String filters) {
        return engine.searchPrefix(prefix, filters);
    }

    @JSExport
    public static String deleteAll(String keyword, String filters) {
        return engine.deleteAll(keyword, filters);
    }

    @JSExport
    public static String deleteAllPrefix(String prefix, String filters) {
        return engine.deleteAllPrefix(prefix, filters);
    }

    @JSExport
    public static String setMaxDocuments(int limit) {
        return engine.setMaxDocuments(limit);
    }

    @JSExport
    public static String setMaxBytes(int limit) {
        return engine.setMaxBytes(limit);
    }

    @JSExport
    public static String snapshot() {
        return engine.snapshot();
    }

    @JSExport
    public static String indexView(String text) {
        return engine.indexView(text);
    }

    @JSExport
    public static String diskJson(String name) {
        return engine.diskJson(name);
    }
}
