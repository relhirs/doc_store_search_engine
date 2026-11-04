#!/bin/sh
# Builds the engine, runs its tests, then requires the JVM and TeaVM to give byte-identical responses.
set -e
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
cd "$(dirname "$0")"

(cd ../../stage6 && mvn -q install -DskipTests)
mvn -q clean package

JAR="$HOME/.m2/repository/edu/yu/cs/com1320/document-store-stage6/1.0-SNAPSHOT/document-store-stage6-1.0-SNAPSHOT.jar"
"$JAVA_HOME/bin/java" -cp "target/classes:$JAR" edu.yu.cs.com1320.project.demo.ParityJvm target/parity-jvm.txt
cp target/javascript/parity.js target/parity.mjs
cp target/javascript/engine.js target/engine.mjs
node check.mjs "$PWD/target"
cmp target/parity-jvm.txt target/parity-teavm.txt
echo "JVM and TeaVM responses are byte-identical: $(wc -c < target/parity-jvm.txt | tr -d ' ') bytes"
echo "engine.js: $(wc -c < target/javascript/engine.js | tr -d ' ') bytes, $(gzip -9c target/javascript/engine.js | wc -c | tr -d ' ') gzipped"
