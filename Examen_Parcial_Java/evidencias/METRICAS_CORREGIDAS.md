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

- **PCP-10 (#10):** OPEN al 2026-10-27; su regresión sigue pendiente. Las mediciones P2 son preliminares hasta que PCP-10 quede integrado y validado.
- **PCP-11 (#11):** OPEN y bloqueado por falta de una segunda computadora física. No se ejecutó ni se declara una prueba LAN. La preparación operativa se publicó como comentario del issue #11.
- **PCP-12 (#12):** OPEN. Queda pendiente validar P2 sobre el estado final de PCP-10. Estas mediciones no satisfacen una prueba física de red, y no justifican cerrar #12.
- Las métricas son de este equipo, este dataset y estas tres repeticiones; no son una afirmación de rendimiento general.
