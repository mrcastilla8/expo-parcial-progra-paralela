# Contrato técnico de análisis multidimensional — Pregunta 1

Este documento fija el formato de entrada, las operaciones A–H y los resultados comparables de P1. Es la fuente de verdad para las implementaciones serial y paralela. Las rutas indicadas son relativas a `Examen_Parcial_Java/Pregunta1/` y los nombres distinguen mayúsculas y minúsculas.

## 1. Alcance y topología

El examen pide una aplicación Java serial y paralela con paralelismo orientado a tareas, ocho nodos con dependencias funcionales, y búsqueda de patrones y tendencias entre variables de observaciones multidimensionales. Este contrato concreta el análisis de datos para cumplir ese objetivo. No define aquí la implementación de las clases Java.

El DAG contractual es exactamente:

```text
A ─┐
   ├─> C ─> D ─> F ─┐
B ─┘       └─> E ─> G ─┴─> H
```

En notación de dependencias: `A y B → C`, `C → D → F`, `C → E → G`, `F y G → H`. No hay otras tareas, dependencias, eventos ni servicios.

## 2. Dataset de entrada

### 2.1 Archivos, codificación y esquema

Las dos entradas son `dataset/parteA.csv` y `dataset/parteB.csv`. Cada archivo es CSV UTF-8 sin BOM, separado por comas y con punto como separador decimal. La primera línea debe ser exactamente:

```csv
instante,temperatura,humedad,consumo_energia,demanda
```

Cada línea posterior contiene exactamente cinco campos, sin comillas CSV ni espacios exteriores. Las líneas vacías y campos vacíos son inválidos. La entrada puede usar LF o CRLF como terminador de línea; los archivos de salida definidos por este contrato siempre usan LF.

`instante` es un entero decimal positivo que cabe en `long`. Se admiten ceros iniciales, pero no signo. Las cuatro variables restantes son números decimales finitos, con la gramática `^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?$`; por tanto, se acepta notación exponencial. Tras parsear a `double`, el valor debe ser finito. `NaN`, infinito, overflow de parseo, separador decimal coma y cualquier otro texto son inválidos. No se prescriben unidades físicas para datos sintéticos.

El conjunto completo debe contener al menos tres observaciones. Cada parte debe tener al menos una observación. En cada parte los registros están ordenados ascendentemente y sus instantes avanzan de uno en uno. Al concatenar A y B, el primer instante debe ser 1 y la secuencia debe ser exactamente `1..N`, sin huecos, solapamientos ni duplicados. La unión conserva la secuencia de A seguida de B. No se exige un tamaño igual para ambas partes.

### 2.2 Validaciones y errores de entrada

Se produce error de la ejecución si falta una entrada; no puede leerse o decodificarse como UTF-8 válido; contiene BOM; tiene encabezado distinto, columnas adicionales o faltantes; tiene una línea vacía o un campo vacío; contiene un instante inválido, repetido, desordenado o no consecutivo dentro de una parte; contiene un dato decimal inválido o no finito; alguna parte está vacía; el total es menor que tres; o la concatenación no forma exactamente `1..N`. No se corrigen ni descartan registros silenciosamente.

La generación de entradas, cuando se solicite, escribe ambas partes con encabezado y las reglas anteriores. Usa `java.util.Random` inicializado con una semilla fija configurable; la misma semilla y el mismo número de observaciones deben producir exactamente los mismos bytes. La semilla predeterminada es `20261027` y puede cambiarse con la propiedad JVM `pcp.semilla`. El total predeterminado es 100 y puede cambiarse con `pcp.observaciones`; debe ser al menos tres. Para cada fila, en orden ascendente, consume cuatro llamadas consecutivas a `nextDouble()` en el orden de columnas y asigna a cada variable `nextDouble() * 100.0`; serializa esas cuatro cifras con seis decimales y `HALF_UP`. Asigna instantes `1..N`; coloca las primeras `floor(N/2)` filas en A y las restantes en B. Así ambas partes son no vacías y consecutivas. La generación no cambia el contrato de lectura ni las rutas.

## 3. Convenciones numéricas comunes

Las operaciones matemáticas se realizan en `double`, acumulan de izquierda a derecha y en orden ascendente de `instante`. No se reordena la entrada ni se usan agregaciones paralelas. Cada operación verifica que sus resultados intermedios necesarios y resultados finales sean finitos. Un overflow, una división inválida, una raíz inválida o cualquier resultado no finito es error de nodo; nunca se escribe `NaN` ni infinito.

Los resultados numéricos de D–H se formatean con exactamente seis cifras decimales, redondeo `HALF_UP` y convenciones independientes de la región (punto decimal, sin separadores de millares ni notación exponencial). El formato se aplica al valor `double` calculado, convertido a su representación decimal canónica antes de redondear. Si el texto resultante es `-0.000000`, se escribe `0.000000`. Los enteros de conteo se escriben sin parte decimal. El redondeo es solo de presentación; nunca reemplaza valores internos usados por cálculos o clasificación. A, B y C copian los cuatro tokens decimales de entrada literalmente y mantienen intactos los valores de las observaciones; solo se normalizan los terminadores de línea a LF. Esto evita cuantizar la entrada antes del análisis.

La misma entrada, orden y versión de este contrato producen los mismos archivos deterministas. La comparación no depende de fecha, nombres de hilo ni tiempos.

## 4. Definición de operaciones y salidas

Los nombres de las variables y su orden canónico son `temperatura`, `humedad`, `consumo_energia`, `demanda`. Todos los resultados tabulares y textuales se codifican UTF-8 sin BOM y terminan sus líneas con LF; cada fila, incluida la última, lleva un LF final. Los nodos publican sus archivos únicamente después de terminar de generarlos correctamente: escriben a un temporal en el mismo directorio de salida, cierran y validan el temporal, y luego lo mueven al nombre final. Un archivo temporal o incompleto no es un resultado válido. Un error de nodo impide ejecutar sus dependientes; otros nodos ya habilitados e independientes pueden completar. Cualquier error invalida la ejecución completa.

### A y B — lectura y validación de las particiones

| Nodo | Entradas | Operación | Salida | Dependencias | Éxito |
|---|---|---|---|---|---|
| A | `dataset/parteA.csv` | Lee y valida esquema, valores y orden local de la primera partición. | `A.csv` | Ninguna | Publicó todos los registros de A, sin alterarlos, con encabezado contractual. |
| B | `dataset/parteB.csv` | Lee y valida esquema, valores y orden local de la segunda partición. | `B.csv` | Ninguna | Publicó todos los registros de B, sin alterarlos, con encabezado contractual. |

`A.csv` y `B.csv` tienen la cabecera `instante,temperatura,humedad,consumo_energia,demanda` y los registros en el orden original, con los valores de cada observación sin cambios. Cada archivo conserva su secuencia temporal local; la comprobación de que A y B son consecutivos entre sí corresponde a C.

### C — unión y validación global

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `A.csv`, `B.csv` | Verifica ambas cabeceras, concatena filas de A y luego B sin duplicar el encabezado y valida que la secuencia global sea exactamente `1..N`, ordenada y con `N ≥ 3`. | `C.csv` | A y B | Cabecera, orden, continuidad o cardinalidad incompatibles; dato inválido o no finito. | `C.csv` contiene el conjunto completo validado y un solo encabezado. |

### D — estadísticas descriptivas

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `C.csv` | Calcula las estadísticas indicadas abajo para cada una de las cuatro variables. | `D.csv` | C | Error al leer C, `N < 3`, o resultado intermedio/final no finito. | Hay una fila por variable, con conteo `N` y métricas válidas. |

Para una variable `X` con valores `x₁,…,xₙ`, `N` observaciones y media `μX`:

```text
N                 = cantidad de observaciones
μX                = (Σ[i=1..N] xᵢ) / N
SXX               = Σ[i=1..N] (xᵢ − μX)²
desviación         = sqrt(SXX / N)
```

La desviación es poblacional, no muestral. `SXX` es la suma de desviaciones cuadráticas, no la varianza. Una variable constante produce `SXX = 0` y desviación `0`; esto no es error para D.

Cabecera exacta y orden de filas:

```csv
variable,n,media,suma_desviaciones_cuadradas,desviacion_estandar
temperatura,...,...,...,...
humedad,...,...,...,...
consumo_energia,...,...,...,...
demanda,...,...,...,...
```

### E — pendientes lineales

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `C.csv` | Calcula una pendiente de mínimos cuadrados ordinarios de cada variable respecto de `instante`. | `E.csv` | C | Error al leer C o resultado no finito. | Presenta cuatro pendientes en orden canónico. |

Para instantes `tᵢ` y valores `xᵢ`, `t̄ = (Σtᵢ)/N`, `μX = (Σxᵢ)/N`:

```text
pendienteX = [Σ[i=1..N] (tᵢ − t̄)(xᵢ − μX)] / [Σ[i=1..N] (tᵢ − t̄)²]
```

Como los instantes globales son consecutivos y `N ≥ 3`, el denominador es positivo. Cabecera y orden:

```csv
variable,pendiente
temperatura,...
humedad,...
consumo_energia,...
demanda,...
```

### F — correlaciones de Pearson

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `C.csv`, `D.csv` | Calcula correlaciones de Pearson centradas para los seis pares únicos, usando C para los productos cruzados y D para medias y sumas de cuadrados. Verifica que las cuatro filas de D correspondan a las variables y al mismo `N` de C. | `F.csv` | D (y C) | Entradas incompatibles, valor no finito o correlación materialmente fuera de rango. Varianza nula no es error: genera `NO_DEFINIDA`. | Publicó exactamente seis pares en el orden fijado. |

Para las variables `X`, `Y`:

```text
SXY = Σ[i=1..N] (xᵢ − μX)(yᵢ − μY)
SXX = Σ[i=1..N] (xᵢ − μX)²
SYY = Σ[i=1..N] (yᵢ − μY)²
r(X,Y) = SXY / sqrt(SXX * SYY)
```

F lee D y comprueba que su esquema, cuatro variables y conteo correspondan a C; para evitar que el redondeo de presentación altere la correlación, calcula las medias y sumas de cuadrados con precisión interna directamente de los valores de C. Si `SXX = 0` o `SYY = 0`, se escribe exactamente `NO_DEFINIDA`. En otro caso, si el cálculo de `r` queda fuera de `[-1,1]` por no más de `1e-12`, se limita al extremo más cercano del intervalo. Si excede ese margen, el nodo falla; no se oculta el error mediante recorte. Cabecera y orden obligatorio (sin pares invertidos ni duplicados):

```csv
variable_1,variable_2,pearson
temperatura,humedad,...
temperatura,consumo_energia,...
temperatura,demanda,...
humedad,consumo_energia,...
humedad,demanda,...
consumo_energia,demanda,...
```

### G — clasificación de tendencias

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `E.csv` | Clasifica cada pendiente de E con los límites exactos de abajo. | `G.csv` | E | E inválido, incompleto o con pendiente no finita. | Publicó cuatro filas en el orden canónico. |

La categoría se determina con la pendiente interna sin redondear. Como E.csv presenta solo seis decimales, E conserva sus cuatro valores `double` sin redondear en el resultado interno de esa tarea y G los consume tras la dependencia E→G; G lee E.csv para validar filas y escribir la columna pendiente. Este paso de valores entre las tareas existentes no crea un nodo, archivo, evento, dependencia ni servicio adicional.

```text
pendiente >  1e-9  → CRECIENTE
pendiente < -1e-9  → DECRECIENTE
en cualquier otro caso → ESTABLE
```

La columna `pendiente` de G es exactamente la misma representación de seis decimales publicada para esa variable en E. Este redondeo no interviene en la categoría. Cabecera y orden:

```csv
variable,pendiente,tendencia
temperatura,...,...
humedad,...,...
consumo_energia,...,...
demanda,...,...
```

### H — resumen final determinista

| Entradas | Operación | Salida | Dependencias | Error | Éxito |
|---|---|---|---|---|---|
| `F.csv`, `G.csv` | Comprueba su estructura y cardinalidad y produce el resumen canónico siguiente, respetando el orden ya establecido. | `H.txt` | F y G | Entradas inválidas/incompletas o fallo al escribir/publicar el resumen. | Publicó todas las seis correlaciones y cuatro tendencias. |

`H.txt` usa UTF-8 sin BOM, saltos LF, sin líneas vacías y con un LF final. El orden, nombres de secciones, campos y separadores son exactamente:

```text
CORRELACIONES
temperatura|humedad=<pearson>
temperatura|consumo_energia=<pearson>
temperatura|demanda=<pearson>
humedad|consumo_energia=<pearson>
humedad|demanda=<pearson>
consumo_energia|demanda=<pearson>
TENDENCIAS
temperatura|<pendiente>|<tendencia>
humedad|<pendiente>|<tendencia>
consumo_energia|<pendiente>|<tendencia>
demanda|<pendiente>|<tendencia>
```

Cada `<pearson>` es un número con seis decimales o `NO_DEFINIDA`. Cada `<pendiente>` tiene seis decimales y cada `<tendencia>` es una de `CRECIENTE`, `DECRECIENTE`, `ESTABLE`. No se incluyen fechas, nombres de hilos, duraciones ni valores derivados de ejecución.

**Ejemplo ilustrativo de formato, no resultado medido:** para tres registros sintéticos donde `temperatura = 1,2,3`, `humedad = 3,2,1`, `consumo_energia = 5,5,5` y `demanda = 1,3,5`, H sería:

```text
CORRELACIONES
temperatura|humedad=-1.000000
temperatura|consumo_energia=NO_DEFINIDA
temperatura|demanda=1.000000
humedad|consumo_energia=NO_DEFINIDA
humedad|demanda=-1.000000
consumo_energia|demanda=NO_DEFINIDA
TENDENCIAS
temperatura|1.000000|CRECIENTE
humedad|-1.000000|DECRECIENTE
consumo_energia|0.000000|ESTABLE
demanda|2.000000|CRECIENTE
```

## 5. Ejecuciones serial y paralela

Ambos modos ejecutan exactamente las operaciones A–H y publican los mismos formatos. El modo serial respeta este orden:

```text
A → B → C → D → E → F → G → H
```

El modo paralelo usa ocho instancias explícitas de `Thread`, una por nodo, con `start()` y `join()` o coordinación básica equivalente. A y B pueden ejecutarse simultáneamente; C espera a A y B; D y E pueden ejecutarse simultáneamente después de C; F espera a D; G espera a E; H espera a F y G. El hilo principal observa y comprueba la terminación correcta de todos los nodos antes de declarar éxito. Los errores de cualquier hilo se propagan al proceso principal. `ExecutorService`, `CompletableFuture` y pools de hilos no son mecanismos permitidos para orquestar P1.

## 6. Directorios, trazas, publicación y fallo

Cada modo utiliza, respectivamente, `salida/serial/` y `salida/paralelo/`. Al comenzar una ejecución se invalida `EXITO.txt` previo y se retiran los resultados finales A–H previos de ese modo para evitar que datos de una corrida anterior satisfagan una dependencia de la actual. Los temporales abandonados no cuentan como resultados.

Cada modo genera `A.csv`–`G.csv`, `H.txt`, `registro.csv` y, solo tras éxito completo de las ocho tareas y publicación de todas sus salidas, `EXITO.txt`. El marcador es la declaración de éxito de la corrida. Un error de entrada, cálculo, hilo, publicación o comparación produce código de proceso distinto de cero y no deja `EXITO.txt` de esa corrida. Los nodos dependientes de un fallo no se ejecutan. Los resultados parciales de nodos que sí finalizaron pueden existir, pero nunca hacen que la corrida sea válida.

`registro.csv` registra los eventos `INICIO`, `FIN` y `ERROR` de los nodos, con nombre del nodo, hilo, tiempos monotónicos y fecha/hora UTC. Usa UTF-8 sin BOM, LF y esta cabecera exacta:

```csv
nodo,evento,hilo,tiempo_monotono_ns,duracion_ns,fecha_utc
```

Los tiempos son nanosegundos (`System.nanoTime()`); la fecha es ISO 8601 UTC. El orden de registro puede reflejar el intercalado de hilos. La duración total del modo se mide con `System.nanoTime()`. Trazas, marcas de tiempo y duraciones son no deterministas y no participan en la comparación.

La escritura de `registro.csv` se intenta también al terminar con error para preservar diagnóstico; un error secundario al escribir la traza no convierte una ejecución fallida en exitosa. El orden real de eventos de error se conserva en el registro.

## 7. Comparación serial/paralela

La comparación calcula SHA-256 de los ocho artefactos deterministas de cada modo: `A.csv`, `B.csv`, `C.csv`, `D.csv`, `E.csv`, `F.csv`, `G.csv` y `H.txt`. No incluye `registro.csv` ni `EXITO.txt`. Cada par de hashes correspondiente debe ser idéntico. La comparación falla y el proceso devuelve código distinto de cero si falta un artefacto, cualquier hash difiere o cualquiera de las ejecuciones no terminó con éxito. Solo se informa igualdad si los ocho pares coinciden.

## 8. Matriz de trazabilidad académica

| Requisito del examen | Decisión de implementación del equipo | Evidencia que producirá el programa | Material docente relacionado |
|---|---|---|---|
| Aplicación Java serial y paralela (multithreading). | Dos modos ejecutan las mismas operaciones A–H; modo paralelo con ocho `Thread` explícitos, conforme a la indicación comunicada por el profesor y alineado con sus ejemplos. | Archivos por modo, `registro.csv` con hilo y eventos, y comparación SHA-256. | `Demo_PCP1.java` (ejecución serial/multihilo); `QR_Paralelo` de las notas (creación y espera de hilos). |
| Paralelismo orientado a tareas. | Cada nodo A–H es una operación sobre archivos con dependencias funcionales definidas en el DAG. | Productos A–H y eventos de inicio/fin/error por nodo. | `TaskDependency` de las notas (dependencias funcionales). |
| Ocho nodos con dependencias funcionales. | Se conserva exactamente A+B→C, C→D→F, C→E→G, F+G→H. | Un artefacto determinista por cada nodo: A–G CSV y H texto. | Modelo de ocho nodos del examen y `TaskDependency`. |
| Análisis de patrones y tendencias entre variables de observaciones multidimensionales. | Dataset sintético de cinco columnas; Pearson, pendientes lineales y clasificación por umbral; CSV específicos, precisión decimal y validación por hashes son decisiones del equipo. | Estadísticas en D, pendientes en E, correlaciones en F, tendencias en G y resumen en H. | `SO_Tools.java` (procesamiento de datos y operaciones entre columnas). |

El examen menciona como ejemplo las correlaciones entre columnas, pero no prescribe Pearson, los nombres de las columnas, este dataset ni un formato CSV exacto. Pearson, regresiones, umbral, formatos, precisión y estrategia de hashes son decisiones del equipo, no requisitos literales del examen. No se copia la implementación completa de los ejemplos docentes.

## 9. Referencias y límites de consulta

El examen recibido `EP - PCP.docx` establece el objetivo general descrito en la matriz y presenta el modelo de ocho procesos; las decisiones detalladas de este contrato provienen del issue PCP-01 y del encargo del equipo. En los materiales docentes disponibles en el ZIP se identificaron `SO_Tools.java` y `Demo_PCP1.java`; las notas de clase DOCX incluyen ejemplos/clases llamados `QR_Paralelo` y `TaskDependency`. Se los referencia conceptualmente sin copiar sus implementaciones. El ZIP no se agrega al repositorio.
