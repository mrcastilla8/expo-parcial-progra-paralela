# Validacion PCP-04

Fecha: 2026-10-27.

## Entorno

- SO: Microsoft Windows 11 Pro 10.0.26300, 64 bits.
- CPU: Intel Core i5-14400F.
- JDK: Eclipse Temurin 17.0.20.1.
- Compatibilidad objetivo: Java 17 mediante `javac --release 17`.

## Comandos ejecutados

Desde `Examen_Parcial_Java/Pregunta1` se uso un directorio de compilacion limpio fuera del repositorio:

```powershell
javac --release 17 -encoding UTF-8 -Xlint:all -d $build src\*.java
java -cp $build PruebaTendencias
java -Xmx256m -cp $build PruebaDataset
```

## Resultado real

```text
COMPILACION_EXIT=0
Pendientes E y tendencias G: OK
PRUEBA_TENDENCIAS_EXIT=0
Generacion y validacion de dataset: OK
PRUEBA_DATASET_EXIT=0
```

`PruebaTendencias` comprobo:

- Muestra lineal de `dataset/`: pendientes `1`, `-1`, `0` y `2` con tendencias `CRECIENTE`, `DECRECIENTE`, `ESTABLE` y `CRECIENTE`; E.csv y G.csv byte a byte.
- Pendiente calculada a mano (`t = 1..4`, `x = 1,3,2,5`, pendiente `1.1`).
- Pendientes de `4e-7` y `-4e-7` presentadas como `0.000000` pero clasificadas `CRECIENTE` y `DECRECIENTE`; `5e-10` clasificada `ESTABLE`.
- Umbral exacto: `1e-9` y `-1e-9` son `ESTABLE`; el `double` inmediato superior o inferior cambia la categoria.
- Determinismo de E.csv y G.csv con 1000 observaciones generadas, cuatro variables en orden canonico.
- Huella SHA-256 de `ResultadoE` igual a la de los bytes de C.csv.
- Rechazo sin publicar E.csv: menos de tres observaciones, cabecera, texto no numerico, instantes desordenados, valor no finito, columnas faltantes, overflow de la media, C.csv ausente y fallo al publicar (sin temporales abandonados).
- Rechazo sin publicar G.csv: `ResultadoE` ausente o de otra ejecucion, E.csv ausente, valor alterado, fila faltante o duplicada, variable alterada, CRLF y BOM.

Las pruebas se verificaron introduciendo errores deliberados en `AnalisisTendencias` (umbral `>=`, division entre `N`, redondeo `HALF_EVEN`, G sin validar E.csv, sin control de no finitos, temporales no borrados); cada uno hizo fallar `PruebaTendencias`.

## Integracion con PR #19

Se compilo `AnalisisTendencias` junto con el `Principal` del PR #19 (PCP-05/06) y un sustituto provisional de `AnalisisCorrelaciones`, que no forma parte de esta entrega. Las firmas `calcularPendientes(Path, Path)` y `clasificar(Path, ResultadoE, Path)` coinciden; `Principal comparar` sobre `dataset/` termino con codigo 0 y SHA-256 identico serial/paralelo. Esto no acredita D/F ni la integracion definitiva, que debe repetirse al incorporar PCP-03.

No se modificaron `Principal.java`, `DatasetMultivariable.java` ni codigo de la rama D→F.
