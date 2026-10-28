# Validación final PCP-05/06

Fecha local: 27 de octubre de 2026 (America/Lima).
Entorno: Windows, Oracle JDK 25.0.1, compilación con --release 17.
Rama: poma. Base integrada: origin/main 7013792.
Dependencias definitivas: PCP-03 (PR #22, commit 80f8f2f) y PCP-04
(PR #21, commit 507cb01), incorporadas mediante merge de main.

## Resultado

La compilación completa y las cinco clases de prueba pasan.
Los modos serial y paralelo generan A.csv–G.csv y H.txt idénticos por SHA-256,
con la muestra versionada (seis observaciones) y con 10000 observaciones
generadas con semilla predeterminada 20261027. Serial y Paralelo se ejecutaron
también como puntos de entrada independientes y devolvieron código 0.
Ya no se utilizan las dependencias provisionales de la exploración anterior.

## Comandos y resultados ejecutados

Desde la raíz del repositorio, las fuentes de Pregunta1/src se compilaron en
un directorio nuevo .validacion/pcp-final/build usando:

```powershell
& 'C:/Program Files/Java/jdk-25/bin/javac.exe' --release 17 -encoding UTF-8 -d .validacion/pcp-final/build (Get-ChildItem Examen_Parcial_Java/Pregunta1/src/*.java).FullName
```

La compilación completa devolvió código 0. Se ejecutó cada clase con
java -cp .validacion/pcp-final/build <clase>:

| Clase | Resultado observado | Código |
|---|---|---|
| PruebaDataset | Generación y validación OK, incluidas 100000 observaciones. | 0 |
| PruebaCorrelaciones | D/F, precisión, huellas, entradas incompatibles y fallos OK. | 0 |
| PruebaTendencias | Pendientes E y tendencias G OK. | 0 |
| PruebaNodosDisponibles | A/B/C: tokens, LF, orden y continuidad; H: formato y rechazo de Pearson inválido OK. | 0 |
| PruebaIntegracion | Orden serial, ocho hilos, DAG, barreras, 16 fallos, SHA-256 y marcadores OK. | 0 |

PruebaIntegracion usa un fixture con Pearson +1/-1, columna constante y tres
categorías de tendencia, y contrasta H con un texto esperado independiente.
Verifica todas las aristas A-C, B-C, C-D, C-E, D-F, E-G, F-H y G-H.
Las barreras en A/B y D/E requieren que ambas operaciones puedan progresar a
la vez; no se exige un solapamiento casual por tiempos.
Cada nodo falla por inyección en serial y paralelo (dieciséis casos).
Se comprueban la propagación al principal, el evento ERROR, ausencia de éxito,
limpieza de productos anteriores y ausencia de INICIO en todos los descendientes.
También se comprueba una pendiente que se muestra como 0.000000 pero conserva
la categoría CRECIENTE y el rechazo de comparaciones con hashes diferentes o
marcadores ausentes.

## Ejecución real por consola

Se usaron directorios aislados bajo .validacion/pcp-final y el classpath
absoluto del build anterior, para conservar las entradas versionadas.

Con una copia exacta del dataset versionado:

```text
java -cp <build-absoluto> Serial
Serial EXIT=0
java -cp <build-absoluto> Paralelo
Paralelo EXIT=0
java -cp <build-absoluto> Principal comparar
Validacion SHA-256 A-H: OK
COMPARAR_MUESTRA_EXIT=0
```

Se verificó la presencia de ambos EXITO.txt tras comparar. Después se retiró
únicamente parteA.csv de esa copia y se ejecutó cada modo sobre sus carpetas
de salida existentes:

```text
Entrada ausente serial EXIT=1, sin EXITO ni sucesores
Entrada ausente paralelo EXIT=1, sin EXITO ni sucesores
```

Las trazas de fallo no contienen INICIO de C–H. La rama independiente B puede
completar en paralelo; nunca habilita C si A falla.

En otro directorio aislado:

```text
java -cp <build-absoluto> Principal generar 10000
java -cp <build-absoluto> Principal comparar
Validacion SHA-256 A-H: OK
COMPARAR_10000_EXIT=0
```

Estas ejecuciones validan corrección y equivalencia; no constituyen el estudio
de rendimiento repetido de PCP-12.

## Hashes de la muestra

Cada hash siguiente coincidió exactamente entre serial y paralelo antes de
las pruebas de entrada ausente:

```text
A.csv fbe88ced5b11d2d0e41955f7e7bbfaec9bb4b434147a4928409a8b9b2cfc6369
B.csv c3ffcc9108081eb12795e9c9660b14dc9861d3ee0d171a212bacec63ddb2a724
C.csv b9373eff334cf2a3d04ce6ac9d48b0156488feb0493e17403480bea531f5cdbb
D.csv b1249b63d44d40ed849cac13906ad499b717d0f8d7b6720b9a1eb480076a5146
E.csv 557e05fa8fa3b5342e895a15f6fa24e6d2d283ef4ece2e4a55a93fafbeaf95b9
F.csv 82853434319725e60e028d40341d750502cf92a42b70608e1e700ef86c156062
G.csv a0c035693a62f6e162304f4acf87bece64ead4df0ad5d7b2b92d0710a062e709
H.txt 737ad63acd4154cb928c47b7b7513270155dc3954836d72e38dd9a74c405b15f
```

## Revisión estática y alcance

- Ocho construcciones de Tarea, cada una crea un Thread nombrado A..H.
- join sobre predecesores y espera de todos desde el principal.
- El modo serial no construye tareas ni hilos de trabajo.
- Sin ExecutorService, CompletableFuture, Future, Executors ni sleep en src.
- git diff --check sin errores.
- Los resultados internos de D/E se publican en el contexto después del retorno
  exitoso de sus productores; F/G los observan después de join.
- No se modificaron algoritmos definitivos de PCP-03/04.

Las pruebas se ejecutaron con permisos fuera del sandbox por las restricciones
de Windows sobre Files.move, ya identificadas en la validación anterior.
No se modificó el algoritmo para evitar el renombrado.

La validación corresponde a PCP-05/06. No sustituye la ampliación de regresión
de PCP-07, la guía académica de PCP-08 ni la actualización del informe final.
