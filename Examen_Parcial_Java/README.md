# Examen parcial de Programación Concurrente y Paralela

Dos proyectos independientes de consola, escritos para Java 17 con biblioteca estándar.
Se compiló con JDK 25 usando `--release 17`. No se necesita Maven, una base de datos ni Internet.
Instalar un JDK 17 o posterior; `java` y `javac` deben estar en PATH o definir JAVA_HOME.

- [Pregunta 1](Pregunta1/README.md): serial, cuatro hilos y ocho procesos de archivos con RandomAccessFile.
- [Pregunta 2](Pregunta2/README.md): transacciones sintéticas y distribución real por sockets TCP entre JVM independientes.
- [Pruebas realizadas](evidencias/PRUEBAS.md): resultados medidos y alcance de la validación.

En Windows ejecutar los scripts `.bat`; en Linux usar `sh scripts/compilar.sh` y
`sh scripts/ejecutar.sh ...` desde la carpeta de cada pregunta. Los scripts ubican
automáticamente la carpeta del proyecto. Los comandos Java directos requieren situarse en ella.
La demostración automática del clúster requiere Python 3 únicamente para iniciar y supervisar
los procesos; los programas Java y la ejecución manual no requieren Python.

Inicio rápido Windows:

```bat
cd Pregunta1
scripts\compilar.bat
scripts\ejecutar.bat comparar
cd ..\Pregunta2
scripts\demo_local.bat 100000
```

El ZIP contiene fuentes, configuraciones locales editables, muestras pequeñas,
scripts y evidencia textual. No incluye clases compiladas ni datasets grandes.

Material revisado: EP - PCP.docx (archivo recibido que corresponde al examen mencionado
como DOC-20261015-WA0036.docx), ClusterComputing_1.java y Configuracion.txt.
El diagrama original fue inspeccionado y coincide con el DAG implementado.
Las aclaraciones del texto del usuario guían el trabajo con archivos en P1;
la búsqueda de ERROR por bloques incorpora búsqueda de patrones del enunciado impreso.
No se calcula correlación estadística ni se afirma implementarla.
El ejemplo docente se usa por su identificación por IP y asignación configurable;
el dominio de P2 es bancario, sin reutilizar UbiGeo ni rutas compartidas absolutas.
