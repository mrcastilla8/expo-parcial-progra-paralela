import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class PruebaDataset {
    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("prueba-dataset-");
        try {
            Path primera = raiz.resolve("primera");
            Path segunda = raiz.resolve("segunda");
            GeneradorDataset.generar(primera, 9, 12345L);
            GeneradorDataset.generar(segunda, 9, 12345L);
            comprobarIguales(primera.resolve("parteA.csv"), segunda.resolve("parteA.csv"));
            comprobarIguales(primera.resolve("parteB.csv"), segunda.resolve("parteB.csv"));
            if (DatasetMultivariable.unir(
                    DatasetMultivariable.leerParte(primera.resolve("parteA.csv")),
                    DatasetMultivariable.leerParte(primera.resolve("parteB.csv"))).size() != 9) {
                throw new AssertionError("Cantidad generada incorrecta");
            }

            Path fixture = raiz.resolve("fixture");
            GeneradorDataset.generar(fixture, 3, GeneradorDataset.SEMILLA_PREDETERMINADA);
            String esperadoA = DatasetMultivariable.CABECERA + "\n"
                    + "1,56.941661,48.709902,42.598267,40.653111\n";
            String esperadoB = DatasetMultivariable.CABECERA + "\n"
                    + "2,1.373859,87.585284,47.737016,91.881021\n"
                    + "3,43.871353,30.143409,73.886418,46.933632\n";
            if (!esperadoA.equals(Files.readString(fixture.resolve("parteA.csv"), StandardCharsets.UTF_8))
                    || !esperadoB.equals(Files.readString(fixture.resolve("parteB.csv"), StandardCharsets.UTF_8))) {
                throw new AssertionError("La secuencia aleatoria o el formato generado cambiaron");
            }
            for (Path parte : new Path[] {fixture.resolve("parteA.csv"), fixture.resolve("parteB.csv")}) {
                var lineas = Files.readAllLines(parte, StandardCharsets.UTF_8);
                for (String linea : lineas.subList(1, lineas.size())) {
                    if (linea.split(",", -1).length != 5) {
                        throw new AssertionError("Un lector CSV independiente no recupero cinco columnas");
                    }
                }
            }

            rechazar(raiz, "cabecera.csv", "otra_cabecera\n1,1,2,3,4\n");
            rechazar(raiz, "texto.csv", DatasetMultivariable.CABECERA + "\n1,abc,2,3,4\n");
            rechazar(raiz, "infinito.csv", DatasetMultivariable.CABECERA + "\n1,1e9999,2,3,4\n");
            rechazar(raiz, "duplicado.csv", DatasetMultivariable.CABECERA
                    + "\n1,1,2,3,4\n1,2,3,4,5\n");
            rechazar(raiz, "bom.csv", "\ufeff" + DatasetMultivariable.CABECERA + "\n1,1,2,3,4\n");
            rechazar(raiz, "cr-aislado.csv", DatasetMultivariable.CABECERA + "\r1,1,2,3,4\r");
            rechazar(raiz, "parte-vacia.csv", DatasetMultivariable.CABECERA + "\n");
            probarUtf8Malformado(raiz);
            probarCrlf(raiz);
            probarContinuidadEntrePartes(raiz);
            probarMinimoGlobal(raiz);
            boolean minimoRechazado = false;
            try {
                GeneradorDataset.generar(raiz.resolve("minimo"), 2, 1L);
            } catch (IllegalArgumentException e) {
                minimoRechazado = true;
            }
            if (!minimoRechazado) {
                throw new AssertionError("N < 3 no fue rechazado");
            }
            probarDatasetGrande(raiz);
            System.out.println("Generacion y validacion de dataset: OK");
        } finally {
            borrar(raiz);
        }
    }

    private static void comprobarIguales(Path a, Path b) throws IOException {
        if (!java.util.Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b))) {
            throw new AssertionError("La generacion no es reproducible");
        }
    }

    private static void rechazar(Path raiz, String nombre, String contenido) throws IOException {
        Path archivo = raiz.resolve(nombre);
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        try {
            DatasetMultivariable.leerParte(archivo);
            throw new AssertionError("Entrada invalida aceptada: " + nombre);
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
    }

    private static void probarUtf8Malformado(Path raiz) throws IOException {
        Path archivo = raiz.resolve("utf8-malformado.csv");
        byte[] cabecera = (DatasetMultivariable.CABECERA + "\n1,").getBytes(StandardCharsets.UTF_8);
        byte[] invalido = java.util.Arrays.copyOf(cabecera, cabecera.length + 9);
        invalido[cabecera.length] = (byte) 0xc3;
        byte[] resto = ",2,3,4\n".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(resto, 0, invalido, cabecera.length + 1, resto.length);
        Files.write(archivo, invalido);
        try {
            DatasetMultivariable.leerParte(archivo);
            throw new AssertionError("UTF-8 malformado fue aceptado");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
    }

    private static void probarCrlf(Path raiz) throws IOException {
        Path archivo = raiz.resolve("crlf.csv");
        Files.writeString(archivo, DatasetMultivariable.CABECERA + "\r\n"
                + "1,1,2,3,4\r\n2,2,3,4,5\r\n", StandardCharsets.UTF_8);
        if (DatasetMultivariable.leerParte(archivo).observaciones().size() != 2) {
            throw new AssertionError("No se leyeron correctamente los terminadores CRLF");
        }
    }

    private static void probarContinuidadEntrePartes(Path raiz) throws IOException {
        Path parteA = raiz.resolve("discontinua-a.csv");
        Path parteB = raiz.resolve("discontinua-b.csv");
        Files.writeString(parteA, DatasetMultivariable.CABECERA + "\n1,1,2,3,4\n",
                StandardCharsets.UTF_8);
        Files.writeString(parteB, DatasetMultivariable.CABECERA + "\n3,2,3,4,5\n4,3,4,5,6\n",
                StandardCharsets.UTF_8);
        try {
            DatasetMultivariable.unir(
                    DatasetMultivariable.leerParte(parteA), DatasetMultivariable.leerParte(parteB));
            throw new AssertionError("Particiones no consecutivas fueron aceptadas");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
    }

    private static void probarMinimoGlobal(Path raiz) throws IOException {
        Path archivo = raiz.resolve("completo-minimo.csv");
        Files.writeString(archivo, DatasetMultivariable.CABECERA
                + "\n1,1,2,3,4\n2,2,3,4,5\n", StandardCharsets.UTF_8);
        try {
            DatasetMultivariable.leerCompleto(archivo);
            throw new AssertionError("Dataset completo con menos de tres observaciones fue aceptado");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
    }

    private static void probarDatasetGrande(Path raiz) throws IOException {
        final long cantidad = 100_000;
        Path directorio = raiz.resolve("grande");
        long inicio = System.nanoTime();
        GeneradorDataset.generar(directorio, cantidad, GeneradorDataset.SEMILLA_PREDETERMINADA);
        int leidas = DatasetMultivariable.unir(
                DatasetMultivariable.leerParte(directorio.resolve("parteA.csv")),
                DatasetMultivariable.leerParte(directorio.resolve("parteB.csv"))).size();
        if (leidas != cantidad) {
            throw new AssertionError("Dataset grande incompleto: " + leidas);
        }
        long memoriaUsada = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.printf("Dataset grande: %d observaciones generadas y leidas; tiempo=%.3f ms; memoria_usada_aprox=%d bytes%n",
                cantidad, (System.nanoTime() - inicio) / 1e6, memoriaUsada);
    }

    private static void borrar(Path raiz) throws IOException {
        try (var rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        }
    }
}
