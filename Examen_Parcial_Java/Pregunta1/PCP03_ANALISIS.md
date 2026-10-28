# PCP-03 — Estadísticas D y correlaciones F

Implementación de D/F conforme a `CONTRATO_ANALISIS.md`.

## API de integración

```java
AnalisisCorrelaciones.ResultadoD resultadoD =
        AnalisisCorrelaciones.calcularEstadisticas(c, d);
AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultadoD, f);
```

`c`, `d` y `f` son `Path` a C.csv, D.csv y F.csv. Ambas operaciones pueden
lanzar `IOException`. Las firmas coinciden con las previstas en el borrador
PCP-05/06 de la rama `poma`.

- D valida C, calcula medias, SXX y desviaciones poblacionales en el orden
  contractual y publica D.csv. Solo después retorna `ResultadoD` inmutable,
  con cantidad, huella SHA-256 de C y medias/SXX sin redondear.
- F verifica los bytes de C contra esa huella y el contenido canónico completo
  de D contra `ResultadoD`. Calcula los seis pares de Pearson centrados usando
  precisión interna; una varianza nula se representa como `NO_DEFINIDA`.
- Cada suma, producto, división y raíz necesaria se comprueba para rechazar
  resultados no finitos. Un denominador de Pearson nulo por subdesbordamiento
  también falla. Solo se recorta un resultado fuera de [-1,1] dentro de 1e-12.
- CSV UTF-8 sin BOM, LF final, seis decimales HALF_UP, sin cero negativo.
  Se escribe, cierra y valida un temporal antes de renombrarlo; se limpia al fallar.

`DatasetMultivariable.leerCompletoConHuella` devuelve un `Documento` inmutable.
Calcula el hash durante la misma lectura que interpreta las observaciones, sobre
los bytes originales (incluidos LF/CRLF), sin una segunda lectura para el hash.
Se conserva el modelo de datos en memoria de PCP-02.

## Contexto de ejecución

El orquestador debe crear un contexto por ejecución, asignar su slot `resultadoD`
únicamente al retorno exitoso de D y entregar ese objeto a F después de esperar D
con `join()`. El objeto no se serializa ni se comparte entre ejecuciones. La API
comprueba contenido y huella; la identidad del contexto es responsabilidad del
orquestador. Al iniciar la ejecución, este invalida productos y EXITO.txt previos;
estas operaciones no generan marcadores de éxito globales.

## Verificación

Desde Pregunta1, con JDK 17 o posterior:

```bat
scripts\compilar.bat
java -cp build PruebaDataset
java -cp build PruebaCorrelaciones
```

`PruebaCorrelaciones` incluye resultados conocidos (+1, -1, 0.5 y varianza nula),
determinismo, precisión inferior a seis decimales, HALF_UP, cero negativo,
configuración regional, hashes de LF/CRLF, D alterado/incompleto, C modificado,
entradas inválidas, ausencia de ResultadoD, overflow/subdesbordamiento y fallos de
publicación. Usa archivos temporales y los elimina al terminar.

La conexión al DAG estadístico completo corresponde a PCP-05/06 junto con E/G
de PCP-04. `Principal` en esta rama todavía ejecuta el flujo histórico de respaldos.
