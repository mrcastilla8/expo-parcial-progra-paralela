# Métricas corregidas PCP-12 (corridas preliminares)

Fecha local: 2026-10-27 (America/Lima). Medición ejecutada sobre el commit `016395b` de `main` (PCP-05/06, PCP-07 y PCP-09 integrados). PCP-10 (#10) continúa abierto; por eso las métricas P2 se consideran **PRELIMINARES** y el issue #12 permanece abierto. Las mediciones P1 son reales para la implementación A–H de ocho `Thread` de ese commit.

## Entorno

- Sistema: Windows 11 Home Single Language, versión 10.0.26200, 64 bits.
- CPU: Intel Core i5-12500H, 12 núcleos / 16 procesadores lógicos.
- RAM instalada: 8 GiB.
- Java/Javac: 17.0.12 (64-bit).
- P1: ocho `Thread` explícitos para A–H.
- P2: cuatro JVM independientes en **una computadora física**, todos enlazados a `127.0.0.1`: coordinador y tres trabajadores; capacidades 4:2:1.

## Metodología reproducible

Las fuentes se compilaron desde checkout limpio con los scripts que usan `javac --release 17`. P1: `Pregunta1/scripts/probar.bat`; P2: `Pregunta2/scripts/compilar.bat`, seguido de las tres clases de prueba listadas abajo. Dataset P1 generado con `Pregunta1/scripts/ejecutar.bat generar 100000` (semilla predeterminada `20261027`). Dataset P2 se generó con `java -cp build Generador dataset/transacciones.csv 100000 1000`; el generador usa semilla fija `20261015` y 1000 cuentas. `demo_local.py` vuelve a generar el mismo CSV para cada repetición; las tres generaciones reproducidas tuvieron SHA idéntico, documentado en `PCP12_P2_DATASET.csv`. Cada modo se ejecutó tres veces sobre los mismos bytes; el programa mide el tiempo total A–H o el tiempo total del coordinador desde inspección hasta escritura/consolidación. La validación SHA se hace fuera del intervalo de P1.

No se modificó el algoritmo para favorecer los tiempos. Las filas CSV conservan los nanosegundos completos. Mediana = valor central de las tres medidas. Speedup = mediana serial / mediana paralelo o distribuido.

## P1 — 100 000 observaciones

Dataset generado con `java -cp build GeneradorDataset 100000 dataset` (semilla predeterminada `20261027`). Entradas: `parteA.csv` 2 268 951 bytes; `parteB.csv` 2 280 205 bytes.

| Modo | Tiempos completos (ns) | Tiempos (ms) | Mediana (ms) |
|---|---:|---:|---:|
| Serial | 898368100; 965979900; 909791800 | 898.368100; 965.979900; 909.791800 | 909.791800 |
| Paralelo (8 `Thread`) | 858239800; 887340100; 877634100 | 858.239800; 887.340100; 877.634100 | 877.634100 |

**Speedup por medianas: 1.036641.** En esta corrida el paralelo fue aproximadamente 3.66% más rápido. El resultado es modesto: las tareas incluyen lectura y escritura de CSV; hay costos de creación y coordinación de ocho hilos, y solo las ramas A/B y D/E pueden solaparse según el DAG. Caché del sistema y variación de almacenamiento también afectan tres repeticiones.

La validación final `Principal comparar` informó `Validacion SHA-256 A-H: OK`. Se cotejaron los ocho archivos A.csv–G.csv y H.txt; los SHA-256 serial/paralelo coinciden, y los hashes exactos están en `PCP12_P1_SHA256.csv`. La corrida adicional de comparación no se incluye en las seis medidas.

## P2 — 100 000 transacciones, localhost, cuatro JVM

El `dataset/transacciones.csv` tuvo SHA-256 `9DB5654B775E442B55D45D39B369FBE922187689BDEC6AB0BB459D84C2D9E329` en las tres repeticiones. Las corridas se lanzaron con `python scripts/demo_local.py 100000`; el script genera datos deterministas con el mismo seed configurado, ejecuta serial y luego inicia el maestro y tres trabajadores. Los logs completos están en `PCP12_P2_LOCAL_corrida_1.log` a `_3.log`.

| Modo | Tiempos completos (ns) | Tiempos (ms) | Mediana (ms) |
|---|---:|---:|---:|
| Serial | 389976500; 352909400; 333359800 | 389.976500; 352.909400; 333.359800 | 352.909400 |
| Distribuido local | 1806479800; 1778631200; 2153787100 | 1806.479800; 1778.631200; 2153.787100 | 1806.479800 |

**Speedup por medianas: 0.195358** (el modo distribuido local tardó aproximadamente 5.12 veces más). Esto es el resultado observado, no una proyección LAN.

En cada repetición se procesaron exactamente **100 000/100 000** transacciones y `serial.csv` / `consolidado.csv` tuvieron el mismo SHA-256 (`FC5B4D14B90E67427A9C501582197F6BE029F37341C23FFC56243044A8A60547`). El reparto final registrado fue nodo1=57 144, nodo2=28 571, nodo3=14 285 (suma 100 000, conforme a 4:2:1 con redondeo); los límites configurados no saturaron las cuotas. `carga.csv` distingue además el procesamiento por nodo.

El cronómetro distribuido incluye inspección y particionado, espera/establecimiento de conexiones, transferencias TCP por loopback, procesamiento concurrente y consolidación/escritura. El lanzador espera explícitamente un segundo después de iniciar el coordinador antes de iniciar los trabajadores; esa espera ocurre dentro del intervalo medido. No se dispone de una subdivisión instrumentada fiable para cada costo de conexión/transferencia en estos logs; por ello no se asignan tiempos estimados a esos componentes. Las cuatro JVM comparten CPU, RAM, cachés y almacenamiento de una computadora física.

## Pruebas y verificación

- P1: compilación Java 17 y seis pruebas de `scripts/probar.bat`: `PruebaDataset`, `PruebaNodosDisponibles`, `PruebaCorrelaciones`, `PruebaTendencias`, `PruebaIntegracion` y `PruebaAnalisis` — PASS.
- P1: tres medidas por modo y comparación SHA de los ocho productos — PASS.
- P2: compilación Java 17; `PruebaBalanceador`, `PruebaTransferencia` y `PruebaInterrupcion` — PASS.
- P2: tres ejecuciones con `demo_local.py`, conteo completo y SHA serial/distribuido — PASS, solo para localhost y cuatro JVM.
- La ejecución local de cuatro JVM no es evidencia de LAN física.

## PCP-10, PCP-11 y límites pendientes

- **PCP-10 (#10):** OPEN; la suite actualizada pasa en PR #25, pero aún no está integrada. La validación P2 permanece preliminar hasta el merge legítimo del PR.
- **PCP-11 (#11):** OPEN y bloqueado por falta de una segunda computadora física. No se ejecutó ni se declara una prueba LAN. La preparación operativa se publicó como comentario del issue #11.
- **PCP-12 (#12):** OPEN. La validación de integración P2 está registrada abajo y pasa provisionalmente; queda confirmar el resultado sobre main después del merge de PCP-10. No satisface una prueba física de red ni permite cerrar #12.
- Las métricas son de este equipo, este dataset y estas tres repeticiones; no son una afirmación de rendimiento general.

## Verificación de integración PCP-12 — provisional

- **Commit de las mediciones repetidas originales:** `016395b1e9d676d6e4d5bd7bce3391a3d78c0068` (implementación P1/P2 medida y dataset originales). Se conservan sin cambios los CSV, logs, tiempos y medianas anteriores.
- **Commit de código validado:** `261a7eac0c544061d725f75ba2a58eccfea36ae6` (main actualizado `ebdcec60` más cambios PCP-10 revisados en PR #25). Validado en worktree temporal `pcp12-integracion`; esos commits de `basualdo` no se incorporaron a la historia de `castilla`.
- **Estado de dependencia:** PR #25 / PCP-10 continúa abierto. La regresión integrada pasó, pero P2 queda **PROVISIONAL** hasta que el PR sea fusionado legítimamente a main. No se declara cerrada la dependencia.

### P1: equivalencia sobre el main reducido

Sin scripts ni suites de prueba de P1, compilé directamente las fuentes presentes con `javac --release 17 -encoding UTF-8 -d build src\*.java`; exit 0. Corrí `java -cp build Serial`, `java -cp build Paralelo` y `java -cp build Principal comparar` sobre el dataset versionado de seis observaciones; los tres terminaron con exit 0. La comparación reportó `Validacion SHA-256 A-H: OK`; los hashes de A.csv–G.csv y H.txt coincidieron. Esta corrida confirma equivalencia funcional sobre la muestra y **no reemplaza** las mediciones repetidas de 100 000 observaciones ni sus speedups.

### P2: regresión PCP-10 y verificación final

`Pregunta2/scripts/probar.bat` ejecutó las cuatro clases (`PruebaBalanceador`, `PruebaTransferencia`, `PruebaInterrupcion`, `PruebaCluster`) con Java 17 y terminó `Pruebas P2: OK`. La prueba corregida fallaría con `SocketTimeoutException`; en esta corrida los rechazos desconocido/duplicado ocurrieron por cierre TCP y los trabajadores legítimos procesaron 20/20. También pasaron el reparto 400/200/100, N/N para clúster normal y disponibilidad parcial, cuota cero, rechazo por almacenamiento insuficiente, sin workers y desconexión sin falso éxito.

`Pregunta2/scripts/demo_local.bat 100000`: PASS, en una computadora física y cuatro JVM sobre `127.0.0.1`; nodo1/nodo2/nodo3 procesaron 57144/28571/14285, total 100000/100000. SHA-256 de `serial.csv` y `consolidado.csv`: `FC5B4D14B90E67427A9C501582197F6BE029F37341C23FFC56243044A8A60547` en ambos archivos. La corrida de verificación (no una repetición estadística) midió 338.987800 ms serial y 1872.960300 ms distribuido; speedup de esa ejecución: 0.1810. El reparto y `procesamiento_ns` se conservaron en `PCP12_P2_VALIDACION_LOCAL_CARGA.csv`.

No se realizaron pruebas entre computadoras; localhost no acredita LAN física. PCP-11 (#11) permanece abierto por falta de una segunda computadora real.
