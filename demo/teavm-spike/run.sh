#!/bin/sh
# Builds the engine with TeaVM and compares its trace against the JVM. Exits non-zero on any real difference.
set -e
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
cd "$(dirname "$0")"

(cd ../../stage6 && mvn -q install -DskipTests)
mvn -q clean package
cp target/javascript/engine.js run/engine.mjs
cp target/javascript/engine.js web/engine.js

M="$HOME/.m2/repository"
CP="target/classes:$M/edu/yu/cs/com1320/document-store-stage6/1.0-SNAPSHOT/document-store-stage6-1.0-SNAPSHOT.jar:$M/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
mkdir -p run/disk
"$JAVA_HOME/bin/java" -cp "$CP" spike.JvmRun map run/jvm-map.txt
"$JAVA_HOME/bin/java" -cp "$CP" spike.JvmRun disk run/jvm-disk.txt run/disk
"$JAVA_HOME/bin/java" -cp "$CP" spike.JvmRun probe run/jvm-probe.txt
node run/run-node.mjs "$PWD/run/engine.mjs" run/teavm.txt

cmp run/jvm-map.txt run/jvm-disk.txt
echo "map-backed disk matches real files"
cd run
python3 ../canon.py
