# Pregunta 1 — Paralelismo orientado a tareas

Analiza observaciones multivariables (temperatura, humedad, consumo de energía y demanda)
con ocho tareas A–H que dependen entre sí. El mismo análisis se ejecuta en modo serial y en
modo paralelo con ocho `Thread`, y ambos resultados se comparan por SHA-256.
Formatos y reglas numéricas completas: [CONTRATO_ANALISIS.md](CONTRATO_ANALISIS.md).

Requisito: JDK 17 o posterior, con `java` y `javac` en PATH o JAVA_HOME definido.
Los scripts usan JAVA_HOME si existe; si `java -version` muestra una versión anterior a 17,
definir JAVA_HOME o los comandos `java` directos fallarán con `UnsupportedClassVersionError`.
Todos los comandos se ejecutan desde la carpeta `Pregunta1`.

## Compilar, ejecutar y probar

```bat
scripts\compilar.bat
scripts\ejecutar.bat serial
scripts\ejecutar.bat paralelo
scripts\ejecutar.bat comparar
scripts\probar.bat
```

En Linux: `sh scripts/compilar.sh`, `sh scripts/ejecutar.sh <modo>` y `sh scripts/probar.sh`.
Los `.sh` se probaron con el `sh` de Git for Windows, no en una distribución Linux.

- `comparar` ejecuta serial y paralelo sobre los mismos datos y verifica que los ocho
  productos sean idénticos (`Validacion SHA-256 A-H: OK`).
- Sin argumentos, `Principal` pide el modo por consola. También existen los puntos de
  entrada `java -cp build Serial` y `java -cp build Paralelo`.
- `probar` compila y ejecuta las seis clases de prueba; termina con código 1 ante la
  primera que falle.

## Datos

`dataset/parteA.csv` y `dataset/parteB.csv` traen una muestra de seis observaciones con
resultado conocido: temperatura y demanda crecen, humedad decrece y el consumo es constante.

Para generar datos reproducibles (el número indica el total de observaciones):

```bat
scripts\ejecutar.bat generar 100000
```

`generar` **reemplaza** la muestra; para recuperarla: `git checkout -- dataset`.
La semilla predeterminada es 20261027. Para usar otra, llamar a Java directamente
(en PowerShell, escribir `'-Dpcp.semilla=7'` entre comillas):

```bat
java -Dpcp.semilla=7 -cp build Principal generar 1000
```

## Tareas y dependencias

```text
A ─┐
   ├─> C ─> D ─> F ─┐
B ─┘       └─> E ─> G ─┴─> H
```

| Nodo | Operación | Producto |
|---|---|---|
| A | Lee y valida `dataset/parteA.csv`. | A.csv |
| B | Lee y valida `dataset/parteB.csv`. | B.csv |
| C | Une A y B y valida la secuencia `1..N` con N ≥ 3. | C.csv |
| D | Media, suma de desviaciones al cuadrado y desviación estándar poblacional por variable. | D.csv |
| E | Pendiente de mínimos cuadrados de cada variable respecto de `instante`. | E.csv |
| F | Correlación de Pearson de los seis pares de variables. | F.csv |
| G | Tendencia de cada pendiente: `CRECIENTE`, `DECRECIENTE` o `ESTABLE`. | G.csv |
| H | Resumen de las correlaciones de F y las tendencias de G. | H.txt |

- **Serial:** ejecuta A, B, C, D, E, F, G, H en orden, en el hilo principal.
- **Paralelo:** crea ocho `Thread` llamados A–H. Cada uno espera con `join()` a sus
  predecesores y solo continúa si terminaron con éxito. Así, A/B y las ramas D→F y E→G
  pueden avanzar a la vez. No se usan pools de hilos, `sleep` ni espera activa.

## Salidas

Cada modo escribe en `salida/serial` o `salida/paralelo`:

- `A.csv`–`G.csv` y `H.txt`: resultados deterministas, iguales en ambos modos.
- `registro.csv`: eventos `INICIO`, `FIN` y `ERROR` por nodo, con hilo, tiempo y fecha UTC.
- `EXITO.txt`: se crea solo si los ocho nodos terminaron bien. Contiene el SHA-256 de H.txt
  y el tiempo total.

Ante un error el programa termina con código 1, no crea `EXITO.txt` y no ejecuta los nodos
que dependen del que falló. No ejecutar dos instancias sobre las mismas carpetas de salida.

## Decisiones del análisis

- Valores con seis decimales (`HALF_UP`); los cálculos internos usan `double` sin redondear.
- Si una variable es constante, su correlación se escribe `NO_DEFINIDA`.
- Una pendiente mayor que `1e-9` es `CRECIENTE`, menor que `-1e-9` es `DECRECIENTE`, y en
  otro caso `ESTABLE`. Por eso una pendiente puede mostrarse como `0.000000` y ser `CRECIENTE`.
- Correlación y pendiente describen asociación, no causalidad.
- El speedup que muestra `comparar` corresponde a una sola corrida; con pocos datos suele
  reflejar el costo de arranque de la JVM más que el paralelismo.

Evidencia de las pruebas: [../evidencias/](../evidencias/) (`PCP02` a `PCP07_VALIDACION.md`).
