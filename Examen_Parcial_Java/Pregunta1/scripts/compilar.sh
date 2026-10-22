#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
JAVAC=javac
if [ -n "${JAVA_HOME:-}" ]; then JAVAC="$JAVA_HOME/bin/javac"; fi
"$JAVAC" --release 17 -encoding UTF-8 -d build src/*.java
