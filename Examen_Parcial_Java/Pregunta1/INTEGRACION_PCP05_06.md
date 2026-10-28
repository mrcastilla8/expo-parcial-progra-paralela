# Integración PCP-05 / PCP-06

La integración usa las implementaciones definitivas de PCP-03 y PCP-04,
incorporadas desde main (7013792; PR #22 y #21). El proyecto completo compila
con Java 17 y ambos modos ejecutan las mismas operaciones sobre el dataset CSV.
La fuente de verdad de formatos y reglas numéricas es CONTRATO_ANALISIS.md.

## Ejecución

Desde Pregunta1, con JDK 17 o posterior en PATH o JAVA_HOME:

```bat
scripts\compilar.bat
scripts\ejecutar.bat serial
scripts\ejecutar.bat paralelo
scripts\ejecutar.bat comparar
```

En Linux usar sh scripts/compilar.sh y sh scripts/ejecutar.sh con los mismos
argumentos. También se conservan los puntos de entrada:

```text
java -cp build Serial
java -cp build Paralelo
java -cp build Principal comparar
```

Las entradas son dataset/parteA.csv y dataset/parteB.csv. Para reemplazarlas
por datos reproducibles: java -cp build Principal generar 10000.
La semilla predeterminada es 20261027; se puede fijar con -Dpcp.semilla=valor
antes de -cp. El argumento indica observaciones totales, no bytes.

## Operaciones y coordinación

| Nodo | Operación | Producto |
|---|---|---|
| A/B | Validar cada partición y normalizar LF conservando los tokens originales. | A.csv/B.csv |
| C | Unir A seguido de B, con una cabecera; validar 1..N y N >= 3. | C.csv |
| D | Calcular estadísticas poblacionales mediante AnalisisCorrelaciones. | D.csv y ResultadoD |
| E | Calcular pendientes mediante AnalisisTendencias. | E.csv y ResultadoE |
| F | Calcular los seis Pearson con C, D y ResultadoD sin redondear. | F.csv |
| G | Clasificar las cuatro pendientes de ResultadoE, validando E.csv. | G.csv |
| H | Validar estructura y cardinalidad de F/G y consolidar el resumen canónico. | H.txt |

No se infiere causalidad de las correlaciones o tendencias.

Serial ejecuta exactamente A,B,C,D,E,F,G,H en el hilo principal.
Paralelo crea ocho Thread llamados A..H. Cada sucesor espera con join a sus
predecesores y comprueba su éxito: A+B -> C; C -> D -> F; C -> E -> G;
F+G -> H. Todos los hilos se inician antes de que el principal espere por ellos.
Los cálculos no usan bloqueo global; A/B y D/E pueden solaparse.

Los resultados inmutables ResultadoD/ResultadoE pertenecen al contexto de la
corrida y se asignan solo cuando el productor termina de publicar D/E.
join garantiza su visibilidad a F/G. No se reconstruyen valores internos a partir
de CSV redondeados ni se mantiene estado estático de resultados.
Las firmas de las implementaciones definitivas coinciden con las llamadas de
Principal; no se incluyen sustitutos ni copias de sus algoritmos.

Los fallos de hilos se comunican al principal y bloquean sus sucesores; ramas
independientes ya habilitadas pueden completar. El principal espera todos los
hilos antes de decidir éxito. La espera tras una interrupción sigue usando join
bloqueante y propaga la interrupción; no consulta flags con espera activa.

## Publicación y comparación

Cada modo escribe en salida/serial o salida/paralelo. Al crear el contexto se
invalidan marcador, resultados y traza previos. Cada producto se publica mediante
temporal y renombrado. registro.csv guarda INICIO/FIN/ERROR, nodo, hilo, reloj
monotónico, duración y fecha UTC; se intenta guardar también ante fallos.
EXITO.txt se publica al finalizar los ocho nodos y guardar la traza.

Comparar exige ambos marcadores y contrasta SHA-256 de A.csv–G.csv y H.txt,
excluyendo tiempos y trazas. Una diferencia o marcador ausente invalida ambos
marcadores. Los errores del programa terminan con código 1. No ejecutar dos
instancias sobre las mismas carpetas de salida.

Se retiran PruebaPatrones y las utilidades binarias sin consumidores.
El informe y las evidencias binarias históricos no validan esta versión.

## Validación

Después de compilar:

```text
java -cp build PruebaDataset
java -cp build PruebaCorrelaciones
java -cp build PruebaTendencias
java -cp build PruebaNodosDisponibles
java -cp build PruebaIntegracion
java -cp build Principal comparar
```

Estas pruebas ya se ejecutaron con las implementaciones definitivas.
PruebaIntegracion verifica el resultado conocido de H, igualdad de los ocho
productos, todas las aristas del DAG, nombres de hilos y orden serial.
Inyecta dieciséis fallos (cada nodo en ambos modos), comprueba ERROR y que
ningún descendiente se inicia ni conserva productos anteriores.
Las barreras de prueba exigen progreso simultáneo A/B y D/E sin depender
del azar del planificador. También verifica clasificación sin pérdida de
precisión y rechazo de comparaciones con hashes distintos o marcador ausente.

La evidencia ejecutada está en ../evidencias/PCP05_06_VALIDACION.md.
La documentación académica completa y las pruebas adicionales de regresión
siguen en el alcance de PCP-08 y PCP-07, respectivamente.
