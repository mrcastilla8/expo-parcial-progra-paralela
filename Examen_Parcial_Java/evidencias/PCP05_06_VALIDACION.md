# Validación de la preparación PCP-05/06

Fecha local: 27 de octubre de 2026 (America/Lima).
Entorno: Windows, Oracle JDK 25.0.1, compilación con --release 17.
Base: origin/main 8b6122f. Rama: poma.

## Estado de entrega

PR en borrador, pendiente de PCP-03/04, según lo solicitado.
No se incluyen implementaciones de AnalisisCorrelaciones ni AnalisisTendencias.
La compilación completa actual falla con código 1 por esas clases ausentes.
No se declaran resueltos los criterios de aceptación completos de #5/#6.

## Validación ejecutada sobre los archivos entregados

Se compilaron explícitamente DatasetMultivariable, GeneradorDataset, Operaciones,
PruebaNodosDisponibles y PruebaDataset con javac --release 17 -encoding UTF-8.
Compilación: código 0.

```text
java -cp .validacion/disponibles PruebaNodosDisponibles
A/B/C: validacion, LF, tokens, orden y continuidad; H: formato y rechazo de Pearson invalido: OK
NODOS_EXIT=0

java -cp .validacion/disponibles PruebaDataset
Dataset grande: 100000 observaciones generadas y leidas; tiempo=744.044 ms; memoria_usada_aprox=140364352 bytes
Generacion y validacion de dataset: OK
DATASET_EXIT=0
```

Los comandos se ejecutaron desde la raíz del repositorio. Los equivalentes
reproducibles desde Pregunta1 están en INTEGRACION_PCP05_06.md.
La primera ejecución restringida falló con AccessDeniedException en Files.move,
también en la prueba preexistente de PCP-02. La repetición con permisos de
ejecución fuera del sandbox pasó. No se cambió el algoritmo para eludir el fallo.

Revisión estática: ocho construcciones de Tarea, cada una crea un Thread;
join en las dependencias; join de todos desde el principal; ausencia de
ExecutorService, CompletableFuture, Future, Executors y sleep en src.
El modo serial no entra a la creación de tareas.

## Exploración local excluida del PR

Antes de recibir la preferencia de dejar pendientes PCP-03/04, se recuperó
localmente D/F del historial y se escribió una implementación provisional E/G.
Con esas dependencias temporales, PruebaIntegracion compiló y pasó el fixture,
SHA-256, barreras, DAG y dieciséis fallos inyectados. Esas clases y la extensión
provisional del lector se retiraron del cambio entregado.

Esa ejecución exploratoria no acredita integración con las implementaciones
definitivas de PCP-03/04. PruebaIntegracion debe volver a compilarse y ejecutarse
tras incorporar esas dependencias, junto con Principal comparar y las pruebas
de fórmulas/entradas adversas de PCP-07. Los artefactos de exploración quedan
fuera del PR y no se usan para declarar aceptación de #5/#6.
