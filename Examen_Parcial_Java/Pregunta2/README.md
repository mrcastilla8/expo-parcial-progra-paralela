# Pregunta 2 — Transacciones bancarias distribuidas

El dataset CSV se genera en disco con semilla reproducible. Cada fila tiene id creciente,
cuenta, fecha, tipo, monto positivo y sucursal. Se calculan cantidad de operaciones,
depósitos, retiros y variación neta por cuenta y global. La variación no es un saldo
bancario real: no hay saldos iniciales. Los resultados se expresan en céntimos enteros.
BigDecimal valida el monto y lo convierte exactamente; Math.addExact detecta desbordes.

## Compilación y pruebas automatizadas

Desde la carpeta `Pregunta2`:

```bat
scripts\compilar.bat
scripts\probar.bat
```

En Linux / entornos POSIX:

```sh
sh scripts/compilar.sh
sh scripts/probar.sh
```

El script `probar.bat` compila y ejecuta las cuatro suites de regresión:
1. `PruebaBalanceador`: pesos 4:2:1, saturación de almacenamiento y rechazo de capacidad insuficiente.
2. `PruebaTransferencia`: rechazo de transferencias truncadas o con hash SHA-256 corrupto sin publicar archivos.
3. `PruebaInterrupcion`: corte abrupto durante transferencias activas, cierre de sockets, espera con `join()` de hilos trabajadores y detección de interrupciones con trabajadores finalizados.
4. `PruebaCluster`: regresión integral del clúster con tres JVMs independientes (4:2:1, integridad SHA-256, unicidad de particiones, ausencia de duplicados), disponibilidad parcial, ausencia de trabajadores, configuración/IP inválida y desconexión intempestiva.

## Ejecución manual y demostración local

Generar dataset y ejecutar modo serial:

```bat
scripts\compilar.bat
java -cp build Generador dataset/transacciones.csv 100000 1000
scripts\ejecutar.bat serial dataset/transacciones.csv resultados
```

Los parámetros de `Generador` son: `ruta_archivo`, `cantidad_registros` y `cantidad_cuentas` (por defecto: 1000).
La muestra mínima incluida es `dataset/muestra.csv`.

Demostración automática con tres trabajadores en **localhost**:

```bat
scripts\demo_local.bat 100000
```
(Linux: `sh scripts/demo_local.sh 100000`).

Inicia cuatro procesos Java independientes comunicándose sobre `127.0.0.1`. Alternativamente,
se pueden abrir cuatro terminales e iniciar el maestro primero y luego los trabajadores:

```bat
java -cp build Coordinador maestro config/cluster.properties dataset/transacciones.csv resultados
java -cp build Trabajador config/cluster.properties nodo1 config/nodo1-ip.txt trabajo/nodo1
java -cp build Trabajador config/cluster.properties nodo2 config/nodo2-ip.txt trabajo/nodo2
java -cp build Trabajador config/cluster.properties nodo3 config/nodo3-ip.txt trabajo/nodo3
```

Los trabajadores deben iniciarse dentro de la ventana de tiempo configurada en `espera.ms` (10 s por defecto).
Cada trabajador procesa un trabajo asignado y finaliza.

Comparar resultados serial vs distribuido:

```bat
java -cp build Coordinador comparar resultados
```

La comparación exige la presencia de `SERIAL-EXITO.txt` y `EXITO.txt`, y valida la igualdad canónica
del hash SHA-256 de `serial.csv` y `consolidado.csv`.

## Modelo de concurrencia y coordinación con Thread

A diferencia de versiones anteriores basadas en pools de hilos (`ExecutorService` / `Future`), el coordinador
ahora implementa concurrencia explícita mediante `Thread` nativos:

1. **Un hilo por conexión:** Cada trabajador admitido es atendido por una instancia explícita de `Thread` (`worker-<id>`).
2. **Sincronización con `join()`:** El coordinador lanza los hilos con `start()` y espera su terminación completa mediante `join()`.
3. **Mapeo posicional:** Los resultados (`Procesamiento.Resumen`) y los posibles errores (`Throwable`) se almacenan en arrays indexados por la posición del nodo.
4. **Manejo robusto de interrupciones:** Ante una interrupción del coordinador (`InterruptedException` o flag activo), se solicita la interrupción a cada hilo hijo (`interrupt()`), se cierran los sockets TCP para desbloquear llamadas de lectura/escritura y se espera con `join()` a que todos los hilos culminen antes de abortar. Se restaura el estado de interrupción del coordinador y nunca se emite `EXITO.txt` ni `consolidado.csv` incompleto.

## Distinción entre pruebas en Localhost y pruebas en LAN física

Es fundamental diferenciar el alcance de las pruebas ejecutadas:

- **Pruebas en Localhost / Loopback (`127.0.0.1`):**
  - Múltiples procesos JVM independientes ejecutándose en el mismo sistema operativo.
  - Validan la corrección del protocolo de red (`BANCO1`), la lógica de balanceo (4:2:1), la transferencia de particiones, la detección de fallos y la equivalencia criptográfica SHA-256 con el modo serial.
  - Al compartir CPU, disco y memoria de un único equipo, los tiempos no representan aceleración física real (*speedup* distribuido).

- **Pruebas en LAN física:**
  - Requieren equipos físicos independientes (laptops/servidores) conectados a la misma subred local.
  - El maestro configura su IP real (`maestro.ip`) en `config/cluster.properties` y abre el puerto TCP de escucha (5000) en el firewall.
  - Cada máquina trabajadora configura su IP física correspondiente en `config/nodoN-ip.txt`.
  - No requiere carpetas compartidas: todos los datos viajan exclusivamente por sockets TCP.
  - Requiere evitar redes Wi-Fi con aislamiento de clientes (AP Isolation) que bloquean la comunicación directa entre terminales.

## Balanceo y protocolo

Coordinador espera los nodos configurados hasta `espera.ms`; si faltan algunos, reparte
entre los conectados (disponibilidad parcial); si no llega ninguno, falla. Las conexiones duplicadas o
desconocidas son rechazadas. No se agregan nodos a mitad del trabajo.

Se inspecciona el dataset sin cargarlo en RAM para contar filas y medir el tamaño máximo
de fila UTF-8 con LF. Para cada nodo, `disponible = min(límite configurado, espacio libre real)`.
Se reserva como máximo un tercio para la partición y el resto para resultado, temporal y margen.
La cuota máxima es `floor((disponible/3 - 1024)/máximo_bytes_fila)`.

El peso divide proporcionalmente las filas pendientes; cuando se alcanza un límite, el excedente se
redistribuye entre nodos aún con espacio. Con pesos 4:2:1 y 700 filas se asignan exactamente 400:200:100.
IDs positivos estrictamente crecientes detectan duplicados y desorden.

## Resultados y fallos

- `resultados/serial.csv` y `consolidado.csv` tienen la misma representación canónica y orden por cuenta.
- `carga.csv` registra IP, capacidad, espacio, filas, bytes y tiempo de procesamiento por nodo.
- Una transferencia truncada, hash incorrecto, desconexión, timeout, configuración inválida, falta de
  espacio o cantidad incorrecta aborta la ejecución sin generar `EXITO.txt`.
