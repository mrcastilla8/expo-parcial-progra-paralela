# Validación PCP-03

Fecha: 2026-10-27. Rama local: `aldana`, base `8b6122f`.

## Entorno y ejecución

- Windows, Eclipse Temurin JDK 21.0.12.1.
- Compilación de todos los fuentes de Pregunta1 con compatibilidad Java 17.
- Directorio de clases fuera del repositorio, bajo
  `C:\Users\david\AppData\Local\Temp\opencode`.
- Las pruebas D/F y dataset se ejecutaron con heap máximo de 256 MiB.

Desde `Examen_Parcial_Java/Pregunta1` (`$build` apunta al directorio de clases):

```powershell
javac --release 17 -encoding UTF-8 -d "$build" src\*.java
java '-Djava.io.tmpdir=C:\Users\david\AppData\Local\Temp\opencode' -Xmx256m -cp "$build" PruebaCorrelaciones
java '-Djava.io.tmpdir=C:\Users\david\AppData\Local\Temp\opencode' -Xmx256m -cp "$build" PruebaDataset
java '-Djava.io.tmpdir=C:\Users\david\AppData\Local\Temp\opencode' -cp "$build" PruebaPatrones
```

## Resultados reales

Compilación y las tres pruebas terminaron con código 0:

```text
PCP-03: D/F, precision, huellas, entradas incompatibles y fallos: OK
Dataset grande: 100000 observaciones generadas y leidas; tiempo=1867.261 ms; memoria_usada_aprox=78886560 bytes
Generacion y validacion de dataset: OK
Busqueda de patrones entre bloques: OK
```

`PruebaCorrelaciones` verificó bytes canónicos de D/F para una muestra conocida,
Pearson positivo/negativo perfecto y no perfecto (0.5), constante decimal 0.1,
determinismo, resultados internos que se presentan como cero, HALF_UP,
normalización de cero negativo e independencia de la configuración regional.

También comprobó huellas independientes de C, incluyendo CRLF, inmutabilidad del
documento, rechazo de C alterado/ausente/inválido, D ausente o alterado en cabecera,
conteo, media, SXX, desviación, orden/cardinalidad o terminadores, y ResultadoD
ausente. Los casos de overflow de suma/cuadrados/producto de Pearson,
subdesbordamiento del denominador y fallo al renombrar fueron rechazados.
No se publicaron archivos parciales ni quedaron temporales. Un resultado previo
se conservó intacto al fallar un nuevo cálculo; el orquestador debe invalidarlo al
inicio de cada ejecución, conforme al contrato.

`PruebaDataset` y `PruebaPatrones` comprobaron que el soporte añadido no rompe
las verificaciones existentes. No se ejecutó el DAG estadístico completo:
la integración PCP-05/06 y E/G de PCP-04 siguen siendo trabajos separados.

En el primer intento de invocación, PowerShell dividió el argumento JVM
`-Djava.io.tmpdir=...` sin comillas y Java no llegó a ejecutar la prueba.
Se corrigió entrecomillando el argumento; los resultados anteriores corresponden
a la ejecución corregida.
