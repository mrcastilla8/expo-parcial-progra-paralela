# Validacion PCP-10 — Regresion del cluster y balanceo tras migrar a Thread

Fecha: 2026-10-27.  
Rama: `basualdo`. Base: `origin/main` (incluye PCP-01 a PCP-07 y PCP-09).  
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
LISTO maestro=127.0.0.1:61023
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=2
Pruebas de interrupcion del coordinador: OK
== PruebaCluster
Generados 700 registros en C:\Users\User\AppData\Local\Temp\cluster-normal-7826956430100841256\transacciones.csv
Registros: 700; tiempo serial: 77.776 ms
LISTO maestro=127.0.0.1:61026
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=400
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=200
Nodo nodo3 IP=127.0.0.1 capacidad=1 registros=100
Registros esperados/procesados: 700/700
Tiempo distribuido: 666.197 ms
Generados 300 registros en C:\Users\User\AppData\Local\Temp\cluster-parcial-11936103931477846552\transacciones.csv
Registros: 300; tiempo serial: 12.742 ms
LISTO maestro=127.0.0.1:61030
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=200
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=100
Registros esperados/procesados: 300/300
Tiempo distribuido: 1613.588 ms
Generados 100 registros en C:\Users\User\AppData\Local\Temp\cluster-sin-workers-3985776346223511193\transacciones.csv
LISTO maestro=127.0.0.1:61033
Generados 50 registros en C:\Users\User\AppData\Local\Temp\cluster-rechazos-1218997548447760902\transacciones.csv
LISTO maestro=127.0.0.1:61034
Conexion rechazada: java.io.IOException: Nodo desconocido
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=50
Generados 100 registros en C:\Users\User\AppData\Local\Temp\cluster-desconexion-11957952650385086874\transacciones.csv
LISTO maestro=127.0.0.1:50879
Nodo nodo1 IP=127.0.0.1 capacidad=1 registros=100
Regresion del cluster y balanceo tras migrar a Thread: OK
Pruebas P2: OK
```

### Salida de `demo_local.py 1000`

```text
Generados 1000 registros en dataset\transacciones.csv
Registros: 1000; tiempo serial: 100.548 ms
LISTO maestro=127.0.0.1:5000
Nodo nodo1 IP=127.0.0.1 capacidad=4 registros=573
Nodo nodo2 IP=127.0.0.1 capacidad=2 registros=285
Nodo nodo3 IP=127.0.0.1 capacidad=1 registros=142
Nodo nodo2 IP=127.0.0.1 recibidos bytes=13356 registros=285
Nodo nodo3 IP=127.0.0.1 recibidos bytes=6697 registros=142
Nodo nodo1 IP=127.0.0.1 recibidos bytes=26768 registros=573
Resultado confirmado: 142
Resultado confirmado: 285
Resultado confirmado: 573
Registros esperados/procesados: 1000/1000
Tiempo distribuido: 1235.833 ms
Validacion SHA-256: OK
Tiempo serial: 100.548 ms
Tiempo distribuido: 1235.833 ms
Speedup: 0.0814
```

### Salida de `demo_local.py 100000`

```text
Generados 100000 registros en dataset\transacciones.csv
Registros: 100000; tiempo serial: 487.874 ms
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
Tiempo distribuido: 1851.814 ms
Validacion SHA-256: OK
Tiempo serial: 487.874 ms
Tiempo distribuido: 1851.814 ms
Speedup: 0.2635
```

---

## 4. Matriz de cobertura y verificacion de PCP-10

| Requisito / Escenario | Clase / Metodo | Estado | Evidencia |
|---|---|---|---|
| Comparación serial vs distribuido (SHA-256) | `PruebaCluster.probarClusterNormal3Jvm` / `demo_local.py` | PASS | `serial.csv` y `consolidado.csv` producen hash SHA-256 idéntico. |
| Totalidad de registros procesados (N de N) | `PruebaCluster` (700 de 700), `demo_local` (100k de 100k) | PASS | Se procesan exactamente todas las filas sin pérdida ni sobrantes. |
| Unicidad de particiones y ausencia de duplicados | `PruebaCluster.probarClusterNormal3Jvm` | PASS | Inspección de `nodo*-entrada.csv`: IDs 1..700 continuos y disjuntos. |
| Reparto 4:2:1 con 3 trabajadores | `PruebaCluster` (400:200:100) y `demo_local` (57144:28571:14285) | PASS | Las cuotas asignadas cumplen estrictamente la proporción de capacidades. |
| Disponibilidad parcial (nodos faltantes) | `PruebaCluster.probarDisponibilidadParcial` | PASS | Se conectan 2 de 3 nodos; el trabajo se reparte y consolida con éxito (300/300). |
| Ausencia de trabajadores (timeout) | `PruebaCluster.probarSinTrabajadores` | PASS | Maestro espera `espera.ms` y aborta; no se publica `EXITO.txt` ni `consolidado.csv`. |
| Configuración o IP inválida | `PruebaCluster.probarConfiguracionEIpInvalida` | PASS | IPs malformadas o ajenas a la máquina son rechazadas con error. |
| Trabajador desconocido | `PruebaCluster.probarTrabajadorDesconocidoYDuplicado` | PASS | Conexión con ID no configurado es rechazada (`Nodo desconocido`). |
| Trabajador duplicado | `PruebaCluster.probarTrabajadorDesconocidoYDuplicado` | PASS | Segunda conexión con mismo ID es rechazada (`IP incorrecta o nodo duplicado`). |
| Almacenamiento insuficiente o cuota cero | `PruebaCluster.probarAlmacenamientoInsuficiente` y `PruebaBalanceador` | PASS | Si cuota total < registros, `Balanceador` lanza excepción y aborta. |
| Desconexión de trabajador durante trabajo | `PruebaCluster.probarDesconexionTrabajador` | PASS | Socket cerrado abruptamente; maestro propaga excepción y no publica `EXITO.txt`. |
| Transferencia truncada / hash corrupto | `PruebaTransferencia` | PASS | Transferencias incompletas o alteradas se rechazan sin publicar archivo temporal. |
| Interrupción con hilos activos / terminados | `PruebaInterrupcion` | PASS | Interrupción cancela hijos, cierra sockets, espera en `join()` y no publica éxito. |

---

## 5. Conclusiones

La suite de regresión demuestra que la migración a concurrencia explícita mediante `Thread` y `join()` en `Coordinador.java` preserva al 100% las garantías de:
1. Exactitud de cálculo e integridad criptográfica SHA-256.
2. Proporción de reparto por capacidad según el `Balanceador`.
3. Ausencia total de falsos positivos o archivos de éxito parcial ante caídas, desconexiones o interrupciones.
