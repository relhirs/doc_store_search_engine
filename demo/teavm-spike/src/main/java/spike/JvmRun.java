package spike;

import edu.yu.cs.com1320.project.stage6.impl.DocumentPersistenceManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JVM reference runs: args are [disk|map] and the trace file to write. */
public final class JvmRun {
    public static void main(String[] args) throws Exception {
        String trace;
        if (args[0].equals("probe")) {
            trace = Script.uriProbe();
        } else if (args[0].equals("disk")) {
            File dir = Files.createTempDirectory(Path.of(args[2]), "disk").toFile();
            trace = Script.run(new DocumentPersistenceManager(dir));
        } else {
            trace = Script.run(new MapPM());
        }
        Files.write(Path.of(args[1]), trace.getBytes(StandardCharsets.UTF_8));
    }
}
