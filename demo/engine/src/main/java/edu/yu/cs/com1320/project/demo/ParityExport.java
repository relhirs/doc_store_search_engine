package edu.yu.cs.com1320.project.demo;

import org.teavm.jso.JSExport;

/** Builds into parity.js only, so the test script doesn't ship in engine.js. */
public final class ParityExport {

    private ParityExport() {
    }

    @JSExport
    public static String runParity() {
        return ParityScript.run();
    }
}
