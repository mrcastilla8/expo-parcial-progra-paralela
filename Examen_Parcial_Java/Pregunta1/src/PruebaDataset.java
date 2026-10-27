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
            boolean minimoRechazado = false;
            try {
                GeneradorDataset.generar(raiz.resolve("minimo"), 2, 1L);
            } catch (IllegalArgumentException e) {
                minimoRechazado = true;
            }
            if (!minimoRechazado) {
                throw new AssertionError("N < 3 no fue rechazado");
            }
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

    private static void borrar(Path raiz) throws IOException {
        try (var rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        }
    }
}
