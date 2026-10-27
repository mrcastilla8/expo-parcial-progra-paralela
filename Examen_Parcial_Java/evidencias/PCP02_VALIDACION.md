# Validacion PCP-02

Fecha: 2026-10-27.

## Entorno

- SO: Microsoft Windows 10 Home 10.0.19045, 64 bits.
- CPU: Intel Core i7-7700HQ, 4 nucleos y 8 procesadores logicos.
- JDK: Eclipse Temurin 21.0.12.1 LTS.
- Compatibilidad objetivo: Java 17 mediante `javac --release 17`.

## Comandos ejecutados

Desde `Examen_Parcial_Java/Pregunta1` se uso un directorio de compilacion limpio fuera del repositorio:

```powershell
javac --release 17 -encoding UTF-8 -d $build src\*.java
java -Xmx256m -cp $build PruebaDataset
```

## Resultado real

```text
COMPILACION_EXIT=0
Dataset grande: 100000 observaciones generadas y leidas; tiempo=1366.119 ms; memoria_usada_aprox=78401200 bytes
Generacion y validacion de dataset: OK
PRUEBA_DATASET_EXIT=0
```

`PruebaDataset` comprobo bytes reproducibles para una semilla conocida, cinco columnas, UTF-8 malformado, LF, CRLF, rechazo de CR aislado, encabezado, texto y valores no finitos, instantes duplicados, discontinuidad entre particiones, partes vacias y minimo global de tres observaciones.

La prueba grande genero y volvio a leer 100000 observaciones con un limite de heap de 256 MiB. No se reprodujo `OutOfMemoryError`; por ello se conserva la implementacion actual con `ArrayList` y no se realiza una refactorizacion de memoria.

No hubo errores inesperados de compilacion o ejecucion. Los casos invalidos produjeron los rechazos esperados y no publicaron un resultado exitoso.
