# Pruebas ejecutadas

Entorno: Windows, JDK 17; compilación con `--release 17`.

- Pregunta 1 (análisis multivariable y ocho Thread): las evidencias anteriores
  permanecen en el historial Git. La entrega actual conserva únicamente su
  implementación y datasets, según el commit `ebdcec6`.
- Pregunta 2: la suite PCP-10 y las demostraciones locales se ejecutaron antes de retirar
  las pruebas y scripts. El informe histórico está en [PCP10_VALIDACION.md](PCP10_VALIDACION.md).
- Las evidencias de PCP-12 permanecen en sus archivos `PCP12_*` y `METRICAS_CORREGIDAS.md`.

Las validaciones de P2 fueron locales, con JVM independientes comunicándose por `127.0.0.1`;
no corresponden a una prueba en varias computadoras físicas ni a mediciones de LAN.
