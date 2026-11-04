# TeaVM spike

Checks that the real Stage 6 engine behaves the same when compiled to JavaScript. `Script.java` runs one fixed sequence of operations and records every result and disk call. `run.sh` compares that record across the JVM with real files, the JVM with a map-backed disk, and TeaVM's output in Node.

    ./run.sh

Needs JDK 17, Maven and Node. To run it in a browser, serve `web/` and open `index.html`. The page runs the same sequence on the main thread and in a Web Worker, then POSTs the result to `/result`, which `serve.py` saves to a file.

The order of disk reads inside ranked searches is the one expected difference, so `canon.py` sorts reads within each step before comparing.
