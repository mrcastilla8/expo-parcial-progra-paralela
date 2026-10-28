# Validacion PCP-07

Fecha: 2026-10-27.
Rama: angel. Base: origin/main 773e6a5 (incluye PCP-03, PCP-04 y PCP-05/06).

## Entorno

- SO: Microsoft Windows 11 Pro 10.0.26300, 64 bits.
- CPU: Intel Core i5-14400F.
- JDK: Eclipse Temurin 17.0.20.1 (via JAVA_HOME).
- Compatibilidad objetivo: Java 17 mediante `javac --release 17`.

## Comandos ejecutados

Desde `Examen_Parcial_Java/Pregunta1`:

```bat
scripts\probar.bat
```

El script compila con `scripts\compilar.bat` y ejecuta las seis clases de prueba; se detiene
con codigo 1 ante la primera que falle. En Linux el equivalente es `sh scripts/probar.sh`;
se ejecuto con el `sh` de Git for Windows, no en una distribucion Linux.

## Resultado real

```text
== PruebaDataset
Generacion y validacion de dataset: OK
== PruebaNodosDisponibles
A/B/C: validacion, LF, tokens, orden y continuidad; H: formato y rechazo de Pearson invalido: OK
== PruebaCorrelaciones
PCP-03: D/F, precision, huellas, entradas incompatibles y fallos: OK
== PruebaTendencias
Pendientes E y tendencias G: OK
== PruebaIntegracion
Integracion serial/8 Thread, DAG, solapamiento con barreras, 16 fallos y SHA-256: OK
== PruebaAnalisis
Referencia independiente, entradas adversas y codigos de salida: OK
Pruebas P1: OK
PROBAR_EXIT=0
```

## Cobertura de PCP-07

| Requisito | Prueba |
|---|---|
| Medias, desviaciones, seis Pearson, pendientes y tres tendencias con resultado conocido | `PruebaCorrelaciones`, `PruebaTendencias` (valores calculados a mano) y `PruebaAnalisis` (referencia independiente) |
| Relacion lineal positiva y negativa, columna constante | `PruebaIntegracion`, `PruebaCorrelaciones`, `PruebaTendencias` |
| N<3, CSV malformado, variable no numerica, instante fuera de orden, entrada faltante | `PruebaAnalisis` a traves de `Principal` en ambos modos; tambien a nivel de clase en `PruebaDataset` y `PruebaTendencias` |
| Error dentro de un nodo | `PruebaIntegracion` (fallo inyectado en cada nodo, 16 casos) y `PruebaAnalisis` (desborde real en D/E) |
| Igualdad serial/paralelo sobre los mismos bytes | `PruebaIntegracion`, `PruebaAnalisis` (serial contra 10 corridas paralelas) y ejecucion por consola |
| Todas las aristas del DAG sin depender de solapamiento casual | `PruebaIntegracion`: FIN antes de INICIO en A-C, B-C, C-D, C-E, D-F, E-G, F-H, G-H; barreras en A/B y D/E |
| Un fallo impide el exito, codigo distinto de cero y sin `EXITO.txt` nuevo | `PruebaAnalisis` (procesos reales `Principal`, `Serial`, `Paralelo`) |

`PruebaAnalisis` agrega:

- Referencia independiente: dataset de 500 observaciones generado con la semilla predeterminada.
  Las medias, sumas cuadraticas, desviaciones, pendientes y Pearson se recalculan en `BigDecimal`
  (`DECIMAL128`) desde los tokens de entrada, con formulas de un paso
  (`S(XY) = suma(xy) - suma(x)suma(y)/N`), distintas de las centradas en `double` de D/E/F.
  Cada valor publicado difiere de la referencia en a lo sumo `1e-6`; las tendencias coinciden
  con el signo de la pendiente de referencia.
- Entradas adversas en serial y paralelo, partiendo de `EXITO.txt` y productos A-H obsoletos:
  parteA ausente, parteB ausente, N=2, columnas faltantes, texto no numerico, cabecera invalida,
  desorden local, hueco y duplicado entre partes y desborde de la suma. En cada caso se comprueba
  el `ERROR` del nodo esperado, la ausencia de `EXITO.txt`, del producto fallido y de H, y que
  ningun descendiente de un nodo con `ERROR` registre `INICIO` ni publique su archivo.
- Procesos independientes: `Principal comparar` devuelve 0 y deja ambos `EXITO.txt`; un modo
  invalido, `Serial` y `Paralelo` con dato no numerico y `comparar` con parteA ausente devuelven
  codigo distinto de cero y eliminan el `EXITO.txt` anterior.

## Sensibilidad de las pruebas

Se introdujeron errores deliberados en copias del codigo y se ejecuto la suite contra cada uno.
Todos fueron detectados:

| Error introducido | Detectado por |
|---|---|
| Un sucesor se ejecuta aunque su predecesor falle | PruebaAnalisis |
| `ejecutar` oculta el error del nodo | PruebaAnalisis |
| `System.exit(0)` ante error | PruebaAnalisis |
| Desviacion estandar 0.00005% mayor | PruebaAnalisis |
| Pendiente 0.01% mayor | PruebaAnalisis |
| Pearson 0.01% menor | PruebaAnalisis |
| F depende de C en lugar de D | PruebaAnalisis |

Con el error de codigo de salida, `probar.bat` y `probar.sh` terminaron con codigo 1.

## Ejecucion por consola con 100000 observaciones

En un directorio aislado (fuera del repositorio), con el build de `scripts\compilar.bat`:

```text
java -cp <build> Principal generar 100000
GENERAR_EXIT=0
java -cp <build> Principal comparar
Validacion SHA-256 A-H: OK
COMPARAR_EXIT=0
```

Hashes identicos entre `salida/serial` y `salida/paralelo`; ambos `EXITO.txt` presentes:

```text
A.csv 1e8276846bd8cbea23174ca798705c9ddaf321ffec0786bc6be221e710d74243
B.csv 5ca0de1a2ea7283db0fd6a144cecd5d714cb7be611b8ad68e0002bca3c1eb0ea
C.csv 99aeb4d07f090e80e8b439bef212d0adcd9bc4bb299c12d1ac07ceeb0fc1113e
D.csv 96cb20dd82e212d68f1c4efad71071131906c44ef13d0abf4c1d1ee6e6e52e86
E.csv 4d099dad0379166d81ece6aa5a8b056c486da4b9a0c0f77b3663ef0f881f333b
F.csv b2a513de2882525897b8a819ea115a9b87a8badfeed2d6c9776dd6bd50f3b0fa
G.csv 8d8a970ed4202999d5e5d9e60207955e606cad37c3aa27d655b0df244fd91e89
H.txt 4f405607a5484200a41f285d9fa6b46dcdef4aefe6002a25fd8d465d1def58af
```

Los tiempos impresos por `comparar` corresponden a una sola corrida y no constituyen la
medicion de PCP-12.

## Evidencia retirada

Se eliminaron `p1_comparacion.log`, `p1_entrada_ausente.log`, `p1_generacion.log`,
`p1_muestra.log`, `p1_paralelo.log`, `p1_paralelo_registro.csv`, `p1_patron_bloques.log`,
`p1_serial.log` y `p1_serial_registro.csv`, y sus lineas en `SHA256SUMS.txt`. Documentaban la
version binaria con `ExecutorService` y la busqueda de ERROR, que ya no existen. Siguen
disponibles en el historial de git (commit a20c23e). `PRUEBAS.md` remite ahora a las
validaciones PCP de la pregunta 1.

## Alcance

No se modifico codigo de produccion. Solo se agregaron `PruebaAnalisis.java` y los scripts
`probar.bat`/`probar.sh`. `SHA256SUMS.txt` conserva entradas antiguas de otros archivos
(por ejemplo `PruebaPatrones.java` y `entrada/`) que corresponden a la auditoria de cierre.
