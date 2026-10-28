#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
sh scripts/compilar.sh
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
for prueba in PruebaBalanceador PruebaTransferencia PruebaInterrupcion PruebaCluster; do
    echo "== $prueba"
    if ! "$JAVA" -cp build "$prueba" > "build/$prueba.log" 2>&1; then
        cat "build/$prueba.log"
        echo "FALLO: $prueba"
        exit 1
    fi
    cat "build/$prueba.log"
done
echo "Pruebas P2: OK"
