#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
exec "$JAVA" -cp build Principal "$@"
