# Pregunta 1 — Paralelismo orientado a tareas

## Avance del análisis multidimensional

PCP-03 incorpora las operaciones estadísticas D/F y sus pruebas independientes.
Consultar [API y validación de PCP-03](PCP03_ANALISIS.md) y
[contrato vigente](CONTRATO_ANALISIS.md). El flujo de `Principal` descrito a
continuación sigue siendo el de respaldos, pendiente de la integración PCP-05/06.

## Flujo histórico de respaldos

Procesa respaldos de registros físicos, por bloques de 64 KiB. Cada nodo publica su
archivo .dat después de cerrar el archivo temporal. Una dependencia solo se habilita
si su antecesor terminó sin error. Una ejecución fallida no genera EXITO.txt.

| Nodo | Operación y archivo |
|---|---|
| A | Copia entrada/registro1.txt a A.dat |
| B | Copia entrada/registro2.txt a B.dat |
| C | Concatena A.dat y B.dat a C.dat |
| D | Replica tres veces C.dat a D.dat |
| E | Copia C.dat a E.dat y convierte ASCII minúscula a mayúscula in situ |
| F | Extrae el 50% central de D.dat a F.dat |
| G | Extrae el 50% central de E.dat a G.dat |
| H | Concatena F.dat y G.dat a H.dat y busca ERROR por bloques |

Las extracciones operan sobre bytes y pueden cortar una línea: son fragmentos binarios
de respaldo. No se interpretan como CSV. La búsqueda conserva el estado entre bloques
mediante KMP, incluso cuando ERROR cruza el límite de lectura.

Dependencias exactas: A+B → C; C → D → F; C → E → G; F+G → H.
Serial ejecuta A,B,C,D,E,F,G,H. Paralelo usa ExecutorService de cuatro hilos y
CompletableFuture.allOf/thenRunAsync, sin esperas artificiales. Cada tarea abre sus
propios RandomAccessFile. Se usan read, write, seek y setLength.

```bat
scripts\compilar.bat
scripts\ejecutar.bat serial
scripts\ejecutar.bat paralelo
scripts\ejecutar.bat comparar
scripts\ejecutar.bat generar 16777216
scripts\ejecutar.bat comparar
```

`generar` reemplaza los dos archivos de entrada con la cantidad de bytes indicada por
archivo. Se incluyen muestras pequeñas; 16 MiB permite observar más actividad de disco.
Sin argumentos Principal lee de consola: escribir serial, paralelo o comparar.
También existen clases Serial y Paralelo: `java -cp build Serial` y `java -cp build Paralelo`.

Salidas: salida/serial y salida/paralelo, ocho archivos A.dat–H.dat, EXITO.txt con
SHA-256 final y tiempo, registro.csv con nodo, evento, hilo, reloj monotónico en ns,
duración en ns y fecha UTC. El tiempo total incluye procesamiento, búsqueda y registro
de consola, y excluye el hash posterior. Comparar verifica SHA-256 de los ocho archivos.
El speedup es tiempo serial / tiempo paralelo; puede ser menor que uno por contención
del disco, caché del sistema y sobrecarga. Una medición no demuestra superioridad general.

No ejecutar dos instancias sobre las mismas carpetas de salida. Los errores muestran
mensaje y código de salida 1. Tras un fallo pueden quedar archivos previos o temporales;
EXITO.txt es el indicador de finalización de esa ejecución.

Prueba puntual: `java -cp build PruebaPatrones` verifica que ERROR se detecte cuando
cruza el límite entre bloques de lectura.
