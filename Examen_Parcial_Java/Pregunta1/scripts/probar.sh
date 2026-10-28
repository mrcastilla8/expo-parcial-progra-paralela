#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
sh scripts/compilar.sh
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
for prueba in PruebaDataset PruebaNodosDisponibles PruebaCorrelaciones PruebaTendencias PruebaIntegracion PruebaAnalisis; do
    echo "== $prueba"
    if ! "$JAVA" -Xmx256m -cp build "$prueba" > "build/$prueba.log" 2>&1; then
        cat "build/$prueba.log"
        echo "FALLO: $prueba"
        exit 1
    fi
    grep -v -E '^[A-H],(INICIO|FIN|ERROR),' "build/$prueba.log" || true
done
echo "Pruebas P1: OK"
