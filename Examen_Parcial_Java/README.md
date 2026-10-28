# Examen parcial de Programación Concurrente y Paralela

Dos proyectos independientes de consola, escritos para Java 17 con biblioteca estándar.
No se necesita Maven ni una base de datos.

- [Pregunta 1](Pregunta1/): análisis serial y paralelo de los archivos CSV incluidos.
- [Pregunta 2](Pregunta2/): transacciones sintéticas y distribución por sockets TCP entre JVM independientes.
- [Pruebas realizadas](evidencias/PRUEBAS.md): resultados medidos y alcance de la validación.

## Pregunta 1

Ejecuta estos comandos desde `Examen_Parcial_Java/Pregunta1/` con JDK 17 o posterior.
La carpeta `dataset/` ya contiene las entradas.

```sh
mkdir -p build
javac --release 17 -encoding UTF-8 -d build src/*.java
java -cp build Serial
java -cp build Paralelo
java -cp build Principal comparar
```

`Principal comparar` ejecuta ambos modos, crea sus resultados en `salida/serial/` y
`salida/paralelo/`, y comprueba mediante SHA-256 que los archivos A–H coincidan.

## Pregunta 2

Desde `Pregunta2/`, compila con `javac --release 17 -encoding UTF-8 -d build src/*.java`.
Ejecuta `Generador`, `Coordinador` y `Trabajador` con los argumentos mostrados por sus clases.

El ZIP contiene fuentes, configuraciones locales editables, muestras pequeñas y evidencia
textual. No incluye clases compiladas ni datasets grandes.

Material revisado: EP - PCP.docx (archivo recibido que corresponde al examen mencionado
como DOC-20261015-WA0036.docx), ClusterComputing_1.java y Configuracion.txt. El diagrama
original fue inspeccionado y coincide con el DAG implementado. Las aclaraciones del texto
del usuario guían el trabajo con archivos en P1; la búsqueda de ERROR por bloques incorpora
búsqueda de patrones del enunciado impreso. No se calcula correlación estadística ni se afirma
implementarla. El ejemplo docente se usa por su identificación por IP y asignación configurable;
el dominio de P2 es bancario, sin reutilizar UbiGeo ni rutas compartidas absolutas.
