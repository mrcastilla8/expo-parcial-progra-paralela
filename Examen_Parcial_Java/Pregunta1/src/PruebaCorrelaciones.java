import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;

public class PruebaCorrelaciones {
    private static final String CABECERA_D =
            "variable,n,media,suma_desviaciones_cuadradas,desviacion_estandar\n";
    private static final String CABECERA_F = "variable_1,variable_2,pearson\n";
    private static final String MUESTRA = DatasetMultivariable.CABECERA + "\n"
            + "1,1,6,0.1,2\n2,2,5,0.1,4\n3,3,4,0.1,6\n"
            + "4,4,3,0.1,8\n5,5,2,0.1,10\n6,6,1,0.1,12\n";

    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("prueba-correlaciones-");
        Locale anterior = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            probarResultadosConocidos(raiz.resolve("conocidos"));
            probarPrecision(raiz.resolve("precision"));
            probarValidaciones(raiz.resolve("validaciones"));
            probarFallosNumericos(raiz.resolve("numericos"));
            probarPublicacion(raiz.resolve("publicacion"));
            try (var rutas = Files.walk(raiz)) {
                if (rutas.anyMatch(p -> p.toString().endsWith(".tmp"))) {
                    throw new AssertionError("Quedaron temporales sin limpiar");
                }
            }
            System.out.println("PCP-03: D/F, precision, huellas, entradas incompatibles y fallos: OK");
        } finally {
            Locale.setDefault(anterior);
            try (var rutas = Files.walk(raiz)) {
                for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(ruta);
            }
        }
    }

    private static void probarResultadosConocidos(Path raiz) throws Exception {
        Path c = escribir(raiz.resolve("C.csv"), MUESTRA);
        Path d = raiz.resolve("D.csv"), f = raiz.resolve("F.csv");
        var resultado = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f);
        igual(CABECERA_D
                + "temperatura,6,3.500000,17.500000,1.707825\n"
                + "humedad,6,3.500000,17.500000,1.707825\n"
                + "consumo_energia,6,0.100000,0.000000,0.000000\n"
                + "demanda,6,7.000000,70.000000,3.415650\n", d);
        igual(CABECERA_F
                + "temperatura,humedad,-1.000000\n"
                + "temperatura,consumo_energia,NO_DEFINIDA\n"
                + "temperatura,demanda,1.000000\n"
                + "humedad,consumo_energia,NO_DEFINIDA\n"
                + "humedad,demanda,-1.000000\n"
                + "consumo_energia,demanda,NO_DEFINIDA\n", f);
        String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(c)));
        if (resultado.cantidad() != 6 || resultado.media(0) != 3.5
                || resultado.sumaCuadrada(0) != 17.5 || !huella.equals(resultado.huellaC())) {
            throw new AssertionError("ResultadoD o huella incorrectos");
        }
        var documento = DatasetMultivariable.leerCompletoConHuella(c);
        try {
            documento.observaciones().clear();
            throw new AssertionError("Documento mutable");
        } catch (UnsupportedOperationException esperado) {
            // Las observaciones y su coleccion son inmutables.
        }
        Path d2 = raiz.resolve("D2.csv"), f2 = raiz.resolve("F2.csv");
        var segundo = AnalisisCorrelaciones.calcularEstadisticas(c, d2);
        AnalisisCorrelaciones.calcularCorrelaciones(c, d2, segundo, f2);
        if (Files.mismatch(d, d2) != -1 || Files.mismatch(f, f2) != -1) {
            throw new AssertionError("D/F no son deterministas");
        }

        escribir(c, DatasetMultivariable.CABECERA + "\n1,1,1,5,-1\n2,2,3,5,-2\n3,3,2,5,-3\n");
        var noPerfecto = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        AnalisisCorrelaciones.calcularCorrelaciones(c, d, noPerfecto, f);
        if (!Files.readString(f).contains("temperatura,humedad,0.500000\n")) {
            throw new AssertionError("Pearson no perfecto incorrecto");
        }
    }

    private static void probarPrecision(Path raiz) throws Exception {
        Path c = escribir(raiz.resolve("C.csv"), DatasetMultivariable.CABECERA
                + "\n1,0.00000001,0.00000002,-0.00000001,1.2345675\n"
                + "2,0.00000002,0.00000004,-0.00000002,1.2345675\n"
                + "3,0.00000003,0.00000006,-0.00000003,1.2345675\n");
        Path d = raiz.resolve("D.csv"), f = raiz.resolve("F.csv");
        var resultado = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f);
        String textoD = Files.readString(d), textoF = Files.readString(f);
        if (resultado.sumaCuadrada(0) <= 0 || resultado.media(0) == 0
                || !textoD.contains("temperatura,3,0.000000,0.000000,0.000000\n")
                || !textoD.contains("demanda,3,1.234568,0.000000,0.000000\n")
                || textoD.contains("-0.000000")
                || !textoF.contains("temperatura,humedad,1.000000\n")
                || !textoF.contains("temperatura,consumo_energia,-1.000000\n")) {
            throw new AssertionError("Se perdio precision interna o formato HALF_UP");
        }
    }

    private static void probarValidaciones(Path raiz) throws Exception {
        Path c = escribir(raiz.resolve("C.csv"), MUESTRA);
        Path d = raiz.resolve("D.csv"), f = raiz.resolve("F.csv");
        var resultado = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        String correctoD = Files.readString(d);
        String[] invalidosD = {
                correctoD.replace("variable,n,", "otra,n,"),
                correctoD.replace("temperatura,6,", "temperatura,7,"),
                correctoD.replace("3.500000", "3.600000"),
                correctoD.replace("17.500000", "17.600000"),
                correctoD.replace("1.707825", "1.707826"),
                correctoD.replace("temperatura", "demanda"),
                correctoD.substring(0, correctoD.lastIndexOf("demanda,")),
                correctoD + "demanda,6,7.000000,70.000000,3.415650\n",
                correctoD.replace("\n", "\r\n"),
                "\ufeff" + correctoD
        };
        for (String invalido : invalidosD) {
            escribir(d, invalido);
            rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f), f);
        }
        escribir(d, correctoD);
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, null, f), f);
        Files.delete(d);
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f), f);
        escribir(d, correctoD);
        // Incluso el mismo dataset numerico con otros bytes invalida ResultadoD.
        escribir(c, MUESTRA.replace("\n", "\r\n"));
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f), f);
        var documentoCrlf = DatasetMultivariable.leerCompletoConHuella(c);
        String hashCrlf = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(c)));
        if (!hashCrlf.equals(documentoCrlf.huellaSha256()) || hashCrlf.equals(resultado.huellaC())) {
            throw new AssertionError("La huella no representa los bytes exactos de C");
        }
        escribir(c, MUESTRA.replace("6,6,1,0.1,12", "6,6,1,0.1,13"));
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f), f);
        String[] invalidosC = {
                DatasetMultivariable.CABECERA + "\n1,1,2,3,4\n2,2,3,4,5\n",
                MUESTRA.replace("3,3,4,0.1,6", "2,3,4,0.1,6"),
                MUESTRA.replace("0.1", "NaN"), "otra\n1,2,3,4,5\n"
        };
        for (String invalido : invalidosC) {
            escribir(c, invalido);
            rechazar(() -> AnalisisCorrelaciones.calcularEstadisticas(c, raiz.resolve("D-invalido.csv")),
                    raiz.resolve("D-invalido.csv"));
        }
        Files.delete(c);
        rechazar(() -> AnalisisCorrelaciones.calcularEstadisticas(c, raiz.resolve("D-ausente.csv")),
                raiz.resolve("D-ausente.csv"));
    }

    private static void probarFallosNumericos(Path raiz) throws Exception {
        Path c = raiz.resolve("C.csv"), d = raiz.resolve("D.csv"), f = raiz.resolve("F.csv");
        escribir(c, DatasetMultivariable.CABECERA
                + "\n1,1e308,1,2,3\n2,1e308,2,3,4\n3,-1e308,3,4,5\n");
        rechazar(() -> AnalisisCorrelaciones.calcularEstadisticas(c, d), d);
        escribir(c, DatasetMultivariable.CABECERA
                + "\n1,1e200,1,2,3\n2,-1e200,2,3,4\n3,0,3,4,5\n");
        rechazar(() -> AnalisisCorrelaciones.calcularEstadisticas(c, d), d);
        // D es finito, pero SXX*SYY desborda en F.
        escribir(c, DatasetMultivariable.CABECERA
                + "\n1,1e100,1e100,2,3\n2,-1e100,-1e100,3,4\n3,0,0,4,5\n");
        var grande = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, grande, f), f);
        // SXX y SYY positivos, pero su producto subdesborda a cero.
        escribir(c, DatasetMultivariable.CABECERA
                + "\n1,1e-100,1e-100,2,3\n2,-1e-100,-1e-100,3,4\n3,0,0,4,5\n");
        var pequeno = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        rechazar(() -> AnalisisCorrelaciones.calcularCorrelaciones(c, d, pequeno, f), f);
    }

    private static void probarPublicacion(Path raiz) throws Exception {
        Path c = escribir(raiz.resolve("C.csv"), MUESTRA);
        Path bloqueada = raiz.resolve("bloqueada.csv");
        escribir(bloqueada.resolve("ocupado.txt"), "impide reemplazar el directorio");
        try {
            AnalisisCorrelaciones.calcularEstadisticas(c, bloqueada);
            throw new AssertionError("D retorno ResultadoD sin publicar");
        } catch (IOException esperado) {
            igual("impide reemplazar el directorio", bloqueada.resolve("ocupado.txt"));
        }
        Path d = raiz.resolve("D.csv"), f = raiz.resolve("F.csv");
        var resultado = AnalisisCorrelaciones.calcularEstadisticas(c, d);
        try {
            AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, bloqueada);
            throw new AssertionError("F informo exito sin publicar");
        } catch (IOException esperado) {
            // El temporal se limpia incluso al fallar el renombrado.
        }
        AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f);
        byte[] anterior = Files.readAllBytes(f);
        escribir(c, MUESTRA.replace("6,6,1,0.1,12", "6,6,1,0.1,13"));
        try {
            AnalisisCorrelaciones.calcularCorrelaciones(c, d, resultado, f);
            throw new AssertionError("C incompatible aceptado");
        } catch (IOException esperado) {
            if (!Arrays.equals(anterior, Files.readAllBytes(f))) {
                throw new AssertionError("Un fallo publico un resultado parcial");
            }
        }
    }

    private static Path escribir(Path archivo, String contenido) throws IOException {
        Files.createDirectories(archivo.getParent());
        return Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
    }

    private static void igual(String esperado, Path archivo) throws IOException {
        if (!Arrays.equals(esperado.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(archivo))) {
            throw new AssertionError("Bytes inesperados en " + archivo + ":\n" + Files.readString(archivo));
        }
    }

    private static void rechazar(Operacion operacion, Path salida) throws Exception {
        try {
            operacion.ejecutar();
            throw new AssertionError("Operacion invalida aceptada: " + salida);
        } catch (IOException esperado) {
            if (Files.exists(salida)) throw new AssertionError("Se publico salida de un calculo fallido: " + salida);
        }
    }

    @FunctionalInterface
    private interface Operacion { void ejecutar() throws Exception; }
}
