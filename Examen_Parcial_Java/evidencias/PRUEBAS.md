# Pruebas ejecutadas

Entorno: Windows, JDK 25.0.1; compilación --release 17.

- p1_generacion: OK
- p1_serial: OK
- p1_paralelo: OK
- p1_comparacion: OK
- p1_patron_bloques: OK
- P1: ocho archivos por modo, dependencias y solapamiento A/B y D/E verificados
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
- p1_muestra: OK
- p2_muestra: OK

Los tiempos reales están en p1_comparacion.log y p2_comparacion.log.
El tiempo distribuido incluye espera inicial y comunicaciones; puede superar al serial.
Tres trabajadores y un maestro se ejecutaron como cuatro JVM independientes en 127.0.0.1.
No se probó en varias computadoras físicas ni se validaron reglas de firewall en una LAN.

Pruebas adicionales:

- P1 entrada ausente: error propagado y sin marcador de exito.
- Transferencia truncada y SHA incorrecto: rechazados sin publicar archivo.
- Limite real configurado de nodo1 a 1 MiB: {'nodo1': 6701, 'nodo3': 31099, 'nodo2': 62200}; 100000 registros conservados, resultado identico.
- Desconexion despues del registro TCP: maestro falla y no publica EXITO.txt.

- Scripts .bat de compilacion y ejecucion de P1: OK.
- Script demo_local.bat de P2: cuatro JVM, 700 registros, reparto 400:200:100 y SHA-256 coincidente (p2_script_demo.log).
- Scripts .sh preparados con saltos LF; no se dispuso de Linux para ejecutarlos.
