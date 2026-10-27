# Validacion PCP-03

Fecha: 2026-10-27.

## Entorno y comandos

Se uso el mismo entorno documentado en `PCP02_VALIDACION.md` y una compilacion limpia de todos los fuentes P1:

```powershell
javac --release 17 -encoding UTF-8 -d $build src\*.java
java -cp $build PruebaCorrelaciones
```

## Resultado real

```text
COMPILACION_EXIT=0
Estadisticas D y correlaciones F: OK
PRUEBA_CORRELACIONES_EXIT=0
```

La prueba verifico el contenido completo de D y F, Pearson positivo y negativo, una columna constante decimal, determinismo, incompatibilidad de C/D/ResultadoD y valores extremos `1e308` que hacen no finita la suma. El calculo extremo fue rechazado antes de publicar D o F; no se escribio `NaN` ni `Infinity`.

No hubo errores inesperados. Las incompatibilidades y operaciones no finitas produjeron los errores esperados.
