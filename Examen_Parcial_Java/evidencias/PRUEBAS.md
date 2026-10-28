# Pruebas ejecutadas

Entorno: Windows, JDK 25.0.1; compilación --release 17.

- Pregunta 1 (análisis multivariable y ocho Thread): las evidencias anteriores
  permanecen en el historial Git. La entrega actual conserva únicamente su
  implementación y datasets, según el commit `ebdcec6`.
- Pregunta 2 (transacciones bancarias distribuidas y concurrencia Thread):
  ver PCP10_VALIDACION.md. Ejecución automatizada completa disponible mediante
  `Pregunta2/scripts/probar.bat` (Linux: `sh Pregunta2/scripts/probar.sh`), que
  valida PruebaBalanceador, PruebaTransferencia, PruebaInterrupcion y PruebaCluster.

- p2_generacion: OK
- p2_serial: OK
- p2_balanceador: OK
- p2_cluster: OK; codigos=[0, 0, 0, 0]
- p2_comparacion: OK
- 100000 registros, pesos 4:2:1: {"nodo3": 14285, "nodo2": 28571, "nodo1": 57144}
- Referencia independiente Python Decimal: cantidades e importes por cuenta OK
- p2_sin_trabajadores: OK (error esperado)
- p2_disponibilidad_parcial: OK; codigos=[0, 0]
- p2_config_invalida: OK (error esperado)
- p2_ip_invalida: OK (error esperado)
- p2_nodo_desconocido: OK (error esperado)
- p2_dataset_minimo: OK
- p2_cuota_cero: OK; codigos=[0, 0, 0, 0]
- p2_serial_minimo: OK
- p2_duplicados: OK (error esperado)
- p2_muestra: OK

Los tiempos reales de P2 están en p2_comparacion.log y PCP10_VALIDACION.md.
El tiempo distribuido incluye espera inicial y comunicaciones; puede superar al serial.
Tres trabajadores y un maestro se ejecutaron como cuatro JVM independientes en 127.0.0.1.
No se probó en varias computadoras físicas ni se validaron reglas de firewall en una LAN.

Pruebas adicionales:

- Transferencia truncada y SHA incorrecto: rechazados sin publicar archivo.
- Limite real configurado de nodo1 a 1 MiB: {'nodo1': 6701, 'nodo3': 31099, 'nodo2': 62200}; 100000 registros conservados, resultado identico.
- Desconexion despues del registro TCP: maestro falla y no publica EXITO.txt.
- Interrupciones de hilos y trabajadores terminados: manejadas de forma segura y sin falsos éxitos.

- Script demo_local.bat de P2: cuatro JVM, 700 registros, reparto 400:200:100 y SHA-256 coincidente (p2_script_demo.log).
- Scripts .sh preparados con saltos LF; no se dispuso de Linux para ejecutarlos.
