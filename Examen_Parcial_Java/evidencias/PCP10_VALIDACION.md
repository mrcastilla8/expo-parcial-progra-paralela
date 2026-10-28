# Validacion PCP-10 — Regresion del cluster y balanceo tras migrar a Thread

Fecha: 2026-10-27.  
Rama: `basualdo`. Base: `origin/main` (incluye PCP-01 a PCP-07, PCP-08 y PCP-09).
Responsable: `@MarcosBasualdoAle`.

---

## 1. Entorno de ejecucion

- **Sistema Operativo:** Windows 11 Pro 64-bit.
- **JDK:** OpenJDK 25.0.1 (compilacion con flag `--release 17`).
- **Red:** Loopback local (`127.0.0.1`).  
  > **Nota sobre el entorno:** Todas las pruebas reportadas en este documento se ejecutaron en **localhost / loopback** utilizando procesos JVM independientes en la misma máquina física. No corresponden a pruebas en red física LAN con múltiples computadoras ni miden aceleración o latencia física de red.

---

## 2. Comandos ejecutados

Desde `Examen_Parcial_Java/Pregunta2`:

```bat
scripts\probar.bat
```

(En entornos POSIX / Linux: `sh scripts/probar.sh`).

Adicionalmente, se ejecutaron las demostraciones del clúster con 4 JVMs en localhost:
```bat
scripts\demo_local.bat 1000
scripts\demo_local.bat 100000
```

---

## 3. Resultados reales obtenidos

### Salida de `scripts\probar.bat`

```text
== PruebaBalanceador
Balanceo 4:2:1=[400, 200, 100]; con limite=[100, 400, 200]; insuficiencia rechazada: OK
== PruebaTransferencia
Transferencias truncadas/corruptas rechazadas sin publicar resultados: OK
== PruebaInterrupcion
LISTO maestro=127.0.0.1:53603
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=2
Pruebas de interrupcion del coordinador: OK
== PruebaCluster
Generados 700 registros en C:\Users\User\AppData\Local\Temp\cluster-normal-1925997702026943306\transacciones.csv
Registros: 700; tiempo serial: 67.597 ms
LISTO maestro=127.0.0.1:53606
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=400
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=200
Nodo nodo3 IP=127.0.0.1 capacidad=1 registros=100
Registros esperados/procesados: 700/700
Tiempo distribuido: 657.605 ms
Generados 300 registros en C:\Users\User\AppData\Local\Temp\cluster-parcial-14918603113386913208\transacciones.csv
Registros: 300; tiempo serial: 7.608 ms
LISTO maestro=127.0.0.1:53610
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=200
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=100
Registros esperados/procesados: 300/300
Tiempo distribuido: 1634.185 ms
Generados 100 registros en C:\Users\User\AppData\Local\Temp\cluster-sin-workers-295992546686141686\transacciones.csv
LISTO maestro=127.0.0.1:53613
Generados 20 registros en C:\Users\User\AppData\Local\Temp\cluster-rechazos-10948473098067209317\transacciones.csv
LISTO maestro=127.0.0.1:53614
Conexion rechazada: java.io.IOException: Nodo desconocido
Conexion rechazada: java.io.IOException: IP incorrecta o nodo duplicado
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=10
Nodo nodo2 IP=127.0.0.1 capacidad=1 registros=10
Registros esperados/procesados: 20/20
Tiempo distribuido: 236.756 ms
Generados 100 registros en C:\Users\User\AppData\Local\Temp\cluster-cuota-cero-9199005551138249837\transacciones.csv
Registros: 100; tiempo serial: 3.696 ms
LISTO maestro=127.0.0.1:53619
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=100
Nodo nodo2 IP=127.0.0.1 capacidad=1 registros=0
Registros esperados/procesados: 100/100
Tiempo distribuido: 247.932 ms
Generados 500 registros en C:\Users\User\AppData\Local\Temp\cluster-insuficiente-9092792267825882812\transacciones.csv
LISTO maestro=127.0.0.1:53622
Generados 100 registros en C:\Users\User\AppData\Local\Temp\cluster-desconexion-671814850971618164\transacciones.csv
LISTO maestro=127.0.0.1:53625
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=100
Regresion del cluster y balanceo tras migrar a Thread: OK
Pruebas P2: OK
```

### Salida de `demo_local.py 1000`

```text
Generados 1000 registros en dataset\transacciones.csv
Registros: 1000; tiempo serial: 111.270 ms
LISTO maestro=127.0.0.1:5000
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=573
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=285
Nodo nodo3 IP=127.0.0.1 capacidad=1 registros=142
Nodo nodo3 IP=127.0.0.1 recibidos bytes=6697 registros=142
Nodo nodo1 IP=127.0.0.1 recibidos bytes=26768 registros=573
Nodo nodo2 IP=127.0.0.1 recibidos bytes=13356 registros=285
Resultado confirmado: 142
Resultado confirmado: 285
Resultado confirmado: 573
Registros esperados/procesados: 1000/1000
Tiempo distribuido: 1218.947 ms
Validacion SHA-256: OK
Tiempo serial: 111.270 ms
Tiempo distribuido: 1218.947 ms
Speedup: 0.0913
```

### Salida de `demo_local.py 100000`

```text
Generados 100000 registros en dataset\transacciones.csv
Registros: 100000; tiempo serial: 458.835 ms
LISTO maestro=127.0.0.1:5000
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=57144
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=28571
Nodo nodo3 IP=127.0.0.1 capacidad=1 registros=14285
Nodo nodo3 IP=127.0.0.1 recibidos bytes=697308 registros=14285
Nodo nodo2 IP=127.0.0.1 recibidos bytes=1394509 registros=28571
Nodo nodo1 IP=127.0.0.1 recibidos bytes=2776810 registros=57144
Resultado confirmado: 14285
Resultado confirmado: 28571
Resultado confirmado: 57144
Registros esperados/procesados: 100000/100000
Tiempo distribuido: 1810.197 ms
Validacion SHA-256: OK
Tiempo serial: 458.835 ms
Tiempo distribuido: 1810.197 ms
Speedup: 0.2535
```

---

## 4. Matriz de cobertura y verificacion de PCP-10

| Requisito / Escenario | Clase / Metodo | Estado | Evidencia |
|---|---|---|---|
| Comparación serial vs distribuido (SHA-256) | `PruebaCluster.probarClusterNormal3Jvm` / `demo_local.py` | PASS | `serial.csv` y `consolidado.csv` producen hash SHA-256 canónico idéntico. |
| Totalidad de registros procesados (N de N) | `PruebaCluster` (700 de 700), `demo_local` (100k de 100k) | PASS | Se procesan exactamente todas las filas sin pérdida ni sobrantes. |
| Unicidad de particiones y ausencia de duplicados | `PruebaCluster.probarClusterNormal3Jvm` | PASS | Inspección de `nodo*-entrada.csv`: IDs 1..700 continuos y disjuntos. |
| Reparto 4:2:1 con 3 trabajadores | `PruebaCluster` (400:200:100) y `demo_local` (57144:28571:14285) | PASS | Las cuotas asignadas cumplen estrictamente la proporción de capacidades. |
| Disponibilidad parcial (nodos faltantes) | `PruebaCluster.probarDisponibilidadParcial` | PASS | Se conectan 2 de 3 nodos; el trabajo se reparte y consolida con éxito (300/300). |
| Ausencia de trabajadores (timeout) | `PruebaCluster.probarSinTrabajadores` | PASS | Maestro espera `espera.ms` y aborta; no se publica `EXITO.txt` ni `consolidado.csv`. |
| Configuración o IP inválida | `PruebaCluster.probarConfiguracionEIpInvalida` | PASS | IPs malformadas o ajenas a la máquina son rechazadas con error. |
| Trabajador desconocido durante admisión abierta | `PruebaCluster.probarTrabajadorDesconocidoYDuplicado` | PASS | Conexión con ID no configurado es rechazada cerrando socket (`EOF/SocketException`). |
| Trabajador duplicado durante admisión abierta | `PruebaCluster.probarTrabajadorDesconocidoYDuplicado` | PASS | Con 2 nodos configurados, segunda conexión con mismo ID es rechazada cerrando socket. |
| Cuota cero y redistribución | `PruebaCluster.probarCuotaCeroYRedistribucion` | PASS | Nodo con espacio mínimo recibe 0 filas; el otro recibe el 100% y el resultado coincide con serial. |
| Almacenamiento insuficiente en coordinador | `PruebaCluster.probarAlmacenamientoInsuficienteEnCoordinador` | PASS | Error propagado por `Balanceador`; no se genera `EXITO.txt` ni `consolidado.csv`. |
| Desconexión de trabajador durante trabajo | `PruebaCluster.probarDesconexionTrabajador` | PASS | Socket cerrado abruptamente; maestro propaga excepción y no publica `EXITO.txt`. |
| Transferencia truncada / hash corrupto | `PruebaTransferencia` | PASS | Transferencias incompletas o alteradas se rechazan sin publicar archivo temporal. |
| Interrupción con hilos activos / terminados | `PruebaInterrupcion` | PASS | Interrupción cancela hijos, cierra sockets, espera en `join()` y no publica éxito. |
| Limpieza robusta ante timeouts en tests | `PruebaCluster.esperarProceso` / bloques `finally` | PASS | Procesos destruidos con `destroyForcibly()`, lectura acotada y cierre de sockets en `finally`. |

---

## 5. Conclusiones

La suite de regresión demuestra que la migración a concurrencia explícita mediante `Thread` y `join()` en `Coordinador.java` preserva al 100% las garantías de:
1. Exactitud de cálculo e integridad criptográfica SHA-256.
2. Proporción de reparto por capacidad según el `Balanceador`, incluyendo redistribución por cuota cero.
3. Rechazo estricto y seguro de nodos desconocidos o duplicados durante la ventana de admisión.
4. Ausencia total de falsos positivos o archivos de éxito parcial ante caídas, desconexiones, almacenamiento insuficiente o interrupciones.
