import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class PruebaCorrelaciones {
    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("prueba-correlaciones-");
        try {
            Path c = raiz.resolve("C.csv");
            Path d = raiz.resolve("D.csv");
            Path f = raiz.resolve("F.csv");
            Files.writeString(c, DatasetMultivariable.CABECERA + "\n"
                    + "1,1,6,0.1,2\n"
                    + "2,2,5,0.1,4\n"
                    + "3,3,4,0.1,6\n"
                    + "4,4,3,0.1,8\n"
                    + "5,5,2,0.1,10\n"
                    + "6,6,1,0.1,12\n", StandardCharsets.UTF_8);

            AnalisisCorrelaciones.ResultadoD resultado =
                    AnalisisCorrelaciones.calcularEstadisticas(c, d);
            AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f);
            String estadisticas = Files.readString(d, StandardCharsets.UTF_8);
            String correlaciones = Files.readString(f, StandardCharsets.UTF_8);
            String estadisticasEsperadas = "variable,n,media,suma_desviaciones_cuadradas,desviacion_estandar\n"
                    + "temperatura,6,3.500000,17.500000,1.707825\n"
                    + "humedad,6,3.500000,17.500000,1.707825\n"
                    + "consumo_energia,6,0.100000,0.000000,0.000000\n"
                    + "demanda,6,7.000000,70.000000,3.415650\n";
            String correlacionesEsperadas = "variable_1,variable_2,pearson\n"
                    + "temperatura,humedad,-1.000000\n"
                    + "temperatura,consumo_energia,NO_DEFINIDA\n"
                    + "temperatura,demanda,1.000000\n"
                    + "humedad,consumo_energia,NO_DEFINIDA\n"
                    + "humedad,demanda,-1.000000\n"
                    + "consumo_energia,demanda,NO_DEFINIDA\n";
            if (!estadisticasEsperadas.equals(estadisticas)) {
                throw new AssertionError("Contenido D inesperado:\n" + estadisticas);
            }
            if (!correlacionesEsperadas.equals(correlaciones)) {
                throw new AssertionError("Contenido F inesperado:\n" + correlaciones);
            }

            Path d2 = raiz.resolve("D2.csv");
            Path f2 = raiz.resolve("F2.csv");
            AnalisisCorrelaciones.ResultadoD resultado2 =
                    AnalisisCorrelaciones.calcularEstadisticas(c, d2);
            AnalisisCorrelaciones.calcularCorrelaciones(c, d2, resultado2, f2);
            if (!java.util.Arrays.equals(Files.readAllBytes(d), Files.readAllBytes(d2))
                    || !java.util.Arrays.equals(Files.readAllBytes(f), Files.readAllBytes(f2))) {
                throw new AssertionError("D o F no son deterministas");
            }

            Files.writeString(c, Files.readString(c, StandardCharsets.UTF_8)
                    .replace("6,6,1,0.1,12", "6,6,1,0.1,13"), StandardCharsets.UTF_8);
            esperarFallo(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f),
                    "C modificado fue aceptado");
            Files.writeString(c, DatasetMultivariable.CABECERA + "\n"
                    + "1,1,6,0.1,2\n2,2,5,0.1,4\n3,3,4,0.1,6\n"
                    + "4,4,3,0.1,8\n5,5,2,0.1,10\n6,6,1,0.1,12\n", StandardCharsets.UTF_8);
            Files.writeString(d, estadisticas.replace("3.500000", "3.600000"), StandardCharsets.UTF_8);
            esperarFallo(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f),
                    "D modificado fue aceptado");
            esperarFallo(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d2, null, f),
                    "ResultadoD ausente fue aceptado");
            System.out.println("Estadisticas D y correlaciones F: OK");
        } finally {
            borrar(raiz);
        }
    }

    private static void esperarFallo(Operacion operacion, String mensaje) throws Exception {
        try {
            operacion.ejecutar();
            throw new AssertionError(mensaje);
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
    }

    @FunctionalInterface
    private interface Operacion {
        void ejecutar() throws Exception;
    }

    private static void borrar(Path raiz) throws IOException {
        try (var rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        }
    }
}
