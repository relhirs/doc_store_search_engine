package edu.yu.cs.com1320.project.demo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes the JVM's side of the parity comparison. */
public final class ParityJvm {

    private ParityJvm() {
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), ParityScript.run().getBytes(StandardCharsets.UTF_8));
    }
}
