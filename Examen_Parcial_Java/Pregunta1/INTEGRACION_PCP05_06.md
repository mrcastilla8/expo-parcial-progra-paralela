# Integración PCP-05 / PCP-06 pendiente de dependencias

Este cambio prepara los nodos A/B/C/H y la coordinación serial y paralela.
Por decisión del solicitante, no implementa PCP-03 (D/F) ni PCP-04 (E/G).
**Es un borrador: la compilación completa y los comandos serial/paralelo/comparar
quedan bloqueados hasta incorporar esas clases y confirmar sus firmas.**
No fusionar este PR mientras falten las dependencias y la validación completa.

## Código preparado

- A/B validan cada partición con DatasetMultivariable y publican CSV normalizados
  a LF, conservando los tokens originales.
- C valida continuidad global 1..N y mínimo de tres filas, conserva el orden
  de A seguido de B y escribe una sola cabecera.
- H valida los seis pares de F y las cuatro variables/categorías de G; escribe
  el resumen canónico del contrato sin interpretaciones causales.
- Serial mantiene A,B,C,D,E,F,G,H sin crear hilos de trabajo.
- Paralelo instancia ocho Thread llamados A..H. Cada uno hace join de sus
  predecesores y verifica su éxito antes de ejecutar la operación.
- Solo la escritura de trazas usa sincronización global. Los cálculos pueden
  solaparse. El principal espera todos los hilos y recibe sus excepciones.
- Los resultados y el marcador se publican con temporal y renombrado.
  Al crear el contexto se invalidan el marcador, resultados y traza previos.
  La traza se intenta guardar también al fallar.
- Comparar contrasta SHA-256 de A.csv–G.csv y H.txt, excluyendo tiempos/trazas;
  una diferencia invalida ambos marcadores.
- Se retira PruebaPatrones junto con las utilidades binarias sin consumidores.
  Las evidencias y el informe históricos no constituyen validación de esta versión.

## Interfaz que deben proporcionar PCP-03/04

Las firmas de PCP-03 se tomaron de la implementación anterior del commit
cea4c71 en aldana, que fue retirada del diff de PCP-02. Las de PCP-04 son una
propuesta de integración; deben conciliarse con su implementación definitiva.

En el paquete por defecto de src, los métodos deben ser estáticos:

```java
// PCP-03
AnalisisCorrelaciones.ResultadoD calcularEstadisticas(Path c, Path d);
void calcularCorrelaciones(Path c, Path d,
        AnalisisCorrelaciones.ResultadoD resultado, Path f);

// PCP-04: propuesta
AnalisisTendencias.ResultadoE calcularPendientes(Path c, Path e);
void clasificar(Path e, AnalisisTendencias.ResultadoE resultado, Path g);
```

Pueden declarar IOException. ResultadoD/ResultadoE deben ser inmutables y
conservar precisión interna y huella de C conforme a CONTRATO_ANALISIS.md.
Los métodos productores retornan el resultado solo después de publicar D/E.
Las cuatro operaciones deben publicar por temporal y renombrado.
Principal asigna cada slot únicamente cuando el método retorna con éxito;
join proporciona la visibilidad a F/G, sin estado estático ni espera activa.

Si PCP-03 utiliza leerCompletoConHuella y Documento, debe incorporarlos en
DatasetMultivariable en su propio cambio. Este borrador conserva ese archivo
tal como está en main y no incorpora implementaciones sustitutas de D/F/E/G.

## Pruebas

Desde Pregunta1, con JDK 17 o superior:

```text
javac --release 17 -encoding UTF-8 -d build-nodos src/DatasetMultivariable.java src/GeneradorDataset.java src/Operaciones.java src/PruebaDataset.java src/PruebaNodosDisponibles.java
java -cp build-nodos PruebaNodosDisponibles
java -cp build-nodos PruebaDataset
```

Estas pruebas no requieren PCP-03/04 y ya se ejecutaron.
PruebaNodosDisponibles comprueba conservación de tokens y filas, LF,
continuidad global, formato de H y rechazo de Pearson fuera de rango.

Después de integrar PCP-03/04 y conciliar las firmas:

```text
scripts/compilar.bat
java -cp build PruebaIntegracion
java -cp build Principal comparar
```

En Linux usar sh scripts/compilar.sh. PruebaIntegracion está preparada para
verificar un resultado conocido de H, identidad SHA-256 de los ocho productos,
todas las aristas del DAG, nombres de hilos, orden serial, dieciséis fallos
inyectados (cada nodo en ambos modos), invalidación de productos anteriores y
clasificación de una pendiente menor que la precisión de presentación.
Usa barreras de prueba para exigir progreso simultáneo A/B y D/E; no depende
de solapamientos casuales del planificador. La prueba completa queda pendiente
con las implementaciones definitivas.

Las guías históricas de respaldo binario necesitan la actualización de PCP-08.
Este documento describe el estado de esta integración, sin cerrar aquel issue.
