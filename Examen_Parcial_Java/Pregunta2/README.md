# Pregunta 2 — Transacciones bancarias distribuidas

El dataset CSV se genera en disco con semilla reproducible. Cada fila tiene id creciente,
cuenta, fecha, tipo, monto positivo y sucursal. Se calculan cantidad de operaciones,
depósitos, retiros y variación neta por cuenta y global. La variación no es un saldo
bancario real: no hay saldos iniciales. Los resultados se expresan en céntimos enteros.
BigDecimal valida el monto y lo convierte exactamente; Math.addExact detecta desbordes.

Compilar y generar (desde Pregunta2):

```bat
scripts\compilar.bat
java -cp build Generador dataset/transacciones.csv 100000 1000
scripts\ejecutar.bat serial dataset/transacciones.csv resultados
```

Los dos últimos parámetros del generador son registros y cuentas (cuentas por defecto: 1000).
Puede generar 1000000 o más registros según disco y tiempo disponibles. La muestra incluida
es dataset/muestra.csv. No modificar el dataset durante una ejecución.

Demostración automática con tres trabajadores: `scripts\demo_local.bat 100000`
(Linux: `sh scripts/demo_local.sh 100000`). Inicia cuatro procesos Java independientes.
La pausa del lanzador solo permite abrir el puerto; no forma parte del procesamiento.
Alternativamente abrir cuatro terminales y ejecutar el maestro primero:

```bat
java -cp build Coordinador maestro config/cluster.properties dataset/transacciones.csv resultados
java -cp build Trabajador config/cluster.properties nodo1 config/nodo1-ip.txt trabajo/nodo1
java -cp build Trabajador config/cluster.properties nodo2 config/nodo2-ip.txt trabajo/nodo2
java -cp build Trabajador config/cluster.properties nodo3 config/nodo3-ip.txt trabajo/nodo3
```

Las cuatro líneas son procesos concurrentes, cada una en su terminal; no esperar a que
termine el maestro para iniciar los trabajadores. Iniciarlos dentro de espera.ms (10 s por
defecto); aumentarlo para una demostración manual. El trabajador se conecta una sola vez,
procesa un trabajo y sale. Reiniciar los trabajadores para otro trabajo.

Después: `java -cp build Coordinador comparar resultados`.
El serial y el distribuido deben usar el mismo dataset. Comparar exige marcadores de
éxito y verifica SHA-256 de todos los resultados ordenados por cuenta, incluyendo TOTAL.

## Configuración e IP

config/cluster.properties declara maestro.ip, maestro.puerto, espera.ms (registro de nodos),
socket.ms (límite de inactividad por lectura TCP), lista nodos y, por cada ID, ip,
capacidad y almacenamiento.mb (MiB). Agregar o quitar un ID y sus propiedades no exige
modificar Java. Se admiten IPv4 numéricas. Las IP de nodo1-ip.txt, etc. se leen realmente.
Se comprueba que la IP pertenece al equipo y coincide con la configuración y la conexión.
Los IDs permiten compartir 127.0.0.1 en pruebas locales.

Guardar/actualizar explícitamente una IP local:
`java -cp build Coordinador ip config/nodo1-ip.txt 127.0.0.1`.
Sin el último argumento usa InetAddress.getLocalHost(); revisar que sea la interfaz LAN
correcta si hay adaptadores virtuales. Se informa error ante IP inválida o ajena al equipo.

Para varias laptops:

1. Conectar todas a la misma LAN y consultar su IPv4 con ipconfig (Windows) o ip addr (Linux).
2. Copiar Pregunta2 a cada equipo y compilar. Elegir un equipo maestro, por ejemplo
   192.168.1.100, y establecer maestro.ip en todas las copias de cluster.properties.
3. Configurar cada nodo con su IP real, peso y límite. Mantener las configuraciones iguales.
   Guardar la IP de cada trabajador en su archivo nodoN-ip.txt. Dos laptops no deben usar
   el mismo ID; usar carpetas de trabajo independientes por proceso.
4. Permitir en el firewall del maestro TCP entrante al puerto elegido (5000 por defecto)
   desde la red privada y permitir conexiones salientes de trabajadores. No se necesitan
   puertos de escucha en trabajadores. Evitar Wi-Fi con aislamiento entre clientes.
5. Generar el dataset solo en el maestro. Iniciar maestro y luego trabajadores con los
   comandos anteriores. Los datos viajan por TCP; no se necesita compartir carpetas.
6. Ejecutar serial y comparar en el maestro. Conservar carga.csv y logs para acreditar
   cuáles equipos participaron. Esta entrega solo fue probada en loopback local.

## Balanceo y protocolo

Coordinador espera los nodos configurados hasta espera.ms; si faltan algunos, reparte
entre los conectados; si no llega ninguno, falla. Las conexiones duplicadas/desconocidas
son rechazadas. No se agregan nodos a mitad del trabajo.

Se inspecciona el dataset sin cargarlo en RAM para contar filas y medir el tamaño máximo
de fila UTF-8 con LF. Para cada nodo, disponible=min(límite configurado, espacio libre
real informado). Se reserva como máximo un tercio para la partición y el resto para
resultado, temporal y margen. La cuota máxima conservadora es
floor((disponible/3 - 1024)/máximo_bytes_fila), limitada a cero como mínimo.
El peso divide proporcionalmente las filas pendientes; cuando se alcanza un límite,
el excedente se redistribuye entre nodos aún con espacio. Restos inferiores a una fila
se asignan en orden de ID. Si no caben todos los registros, el trabajo falla.
Con pesos 4:2:1 y 700 filas se asignan 400:200:100; los límites pueden alterar esa razón.

Se escriben particiones contiguas con cabecera y líneas completas. No se divide una
transacción. IDs positivos estrictamente crecientes detectan duplicados y desorden en
la entrada. Cada partición se asigna exactamente una vez; no hay reintentos que dupliquen
resultados. El maestro transfiere simultáneamente particiones por sockets mediante un
hilo por trabajador. Cada trabajador guarda y verifica SHA-256 antes de procesar.
Transfiere un parcial ordenado por cuenta que se consolida sumando cantidad, depósitos y
retiros, incluso si una cuenta aparece en varios nodos. Los conteos parciales y total se
verifican contra cuotas. SHA-256 comprueba integridad de transferencia; no autentica nodos.

Procesamiento usa BufferedReader y un mapa de agregados por cuenta: memoria O(cuentas
distintas), no O(transacciones); nunca almacena el dataset completo en RAM. Elegir una
cantidad de cuentas acorde con la memoria. Las transferencias usan buffers de 64 KiB.
Este formato CSV deliberadamente no admite comas/campos entrecomillados arbitrarios.

## Resultados y fallos

resultados/serial.csv y consolidado.csv tienen la misma representación canónica.
carga.csv registra IP, capacidad, espacio, filas, bytes y tiempo de procesamiento por nodo.
Carpetas UUID contienen particiones y parciales del maestro. trabajo/nodoN/UUID contiene
los archivos reales del trabajador. Se conservan para inspección; borrarlos entre pruebas
si se necesita liberar almacenamiento. El espacio libre se vuelve a consultar en cada inicio.
No ejecutar dos maestros sobre la misma carpeta de resultados ni compartir carpeta de
trabajo entre trabajadores. El directorio debe pertenecer al proceso que lo usa.

Una transferencia truncada, hash incorrecto, desconexión, timeout, configuración inválida,
falta de espacio o cantidad incorrecta aborta sin EXITO.txt global. Pueden quedar parciales
útiles para diagnóstico. No hay recuperación automática ni garantía ante trabajadores
maliciosos; está diseñado para equipos confiables en una LAN, sin cifrado ni autenticación.

Tiempo serial incluye lectura, agregación y escritura. Tiempo distribuido incluye inspección,
espera inicial de conexiones, particionamiento, red, agregación y escritura; carga.csv separa
el cómputo de cada nodo. El speedup usa estos tiempos completos y no asegura aceleración,
especialmente en localhost, datasets pequeños o almacenamiento compartido.

Clases: Configuracion, Balanceador, Transferencia, Procesamiento, Generador, Coordinador y
Trabajador. PruebaBalanceador verifica pesos, saturación de almacenamiento e insuficiencia.

Pruebas puntuales después de compilar: `java -cp build PruebaBalanceador` y
`java -cp build PruebaTransferencia`. La segunda verifica transferencias truncadas y
hash incorrecto, comprobando que no se publica un archivo incompleto o corrupto.
