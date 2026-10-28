import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/** Operaciones D/F del contrato de P1. No mantiene estado entre ejecuciones. */
public final class AnalisisCorrelaciones {
    private static final List<String> VARIABLES = List.of(
            "temperatura", "humedad", "consumo_energia", "demanda");
    private static final int[][] PARES = {{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};

    private AnalisisCorrelaciones() {}

    public static final class ResultadoD {
        private final long cantidad;
        private final String huellaC;
        private final double[] medias;
        private final double[] sumasCuadradas;

        private ResultadoD(long cantidad, String huellaC, double[] medias, double[] sumasCuadradas) {
            this.cantidad = cantidad;
            this.huellaC = huellaC;
            this.medias = medias.clone();
            this.sumasCuadradas = sumasCuadradas.clone();
        }

        public long cantidad() { return cantidad; }
        public String huellaC() { return huellaC; }
        public double media(int variable) { return medias[variable]; }
        public double sumaCuadrada(int variable) { return sumasCuadradas[variable]; }
    }

    /** Retorna el resultado interno solo despues de publicar D.csv correctamente. */
    public static ResultadoD calcularEstadisticas(Path entradaC, Path salidaD) throws IOException {
        var documento = DatasetMultivariable.leerCompletoConHuella(entradaC);
        var datos = documento.observaciones();
        double[] sumas = new double[VARIABLES.size()];
        boolean[] constantes = new boolean[VARIABLES.size()];
        Arrays.fill(constantes, true);
        for (var observacion : datos) {
            for (int v = 0; v < VARIABLES.size(); v++) {
                sumas[v] = finito(sumas[v] + observacion.valor(v), "suma");
                constantes[v] &= observacion.valor(v) == datos.get(0).valor(v);
            }
        }

        double[] medias = new double[VARIABLES.size()];
        double[] sumasCuadradas = new double[VARIABLES.size()];
        for (int v = 0; v < VARIABLES.size(); v++) {
            medias[v] = finito(sumas[v] / datos.size(), "media");
        }
        for (var observacion : datos) {
            for (int v = 0; v < VARIABLES.size(); v++) {
                double diferencia = finito(observacion.valor(v) - medias[v], "diferencia");
                double cuadrado = finito(diferencia * diferencia, "desviacion cuadratica");
                sumasCuadradas[v] = finito(sumasCuadradas[v] + cuadrado, "suma cuadratica");
            }
        }
        // Una constante decimal como 0.1 puede dejar un residuo de redondeo
        // al acumular su media. El contrato exige varianza exactamente nula.
        for (int v = 0; v < VARIABLES.size(); v++) {
            if (constantes[v]) sumasCuadradas[v] = 0.0;
        }

        ResultadoD resultado = new ResultadoD(datos.size(), documento.huellaSha256(), medias, sumasCuadradas);
        publicar(salidaD, contenidoD(resultado));
        return resultado;
    }

    public static void calcularCorrelaciones(
            Path entradaC, Path entradaD, ResultadoD resultadoD, Path salidaF) throws IOException {
        if (resultadoD == null) throw new IOException("F requiere ResultadoD de la misma ejecucion");
        var documento = DatasetMultivariable.leerCompletoConHuella(entradaC);
        var datos = documento.observaciones();
        if (datos.size() != resultadoD.cantidad || !documento.huellaSha256().equals(resultadoD.huellaC)) {
            throw new IOException("ResultadoD no corresponde al C.csv actual");
        }
        // Comparacion canonica completa: incluye cabecera, orden, conteos,
        // medias, SXX, desviaciones y terminadores, sin reconstruir doubles redondeados.
        if (!contenidoD(resultadoD).equals(Files.readString(entradaD, StandardCharsets.UTF_8))) {
            throw new IOException("D.csv es incompatible con ResultadoD o C.csv");
        }

        StringBuilder salida = new StringBuilder("variable_1,variable_2,pearson\n");
        for (int[] par : PARES) {
            int x = par[0], y = par[1];
            salida.append(VARIABLES.get(x)).append(',').append(VARIABLES.get(y)).append(',');
            if (resultadoD.sumasCuadradas[x] == 0.0 || resultadoD.sumasCuadradas[y] == 0.0) {
                salida.append("NO_DEFINIDA\n");
                continue;
            }
            double sumaCruzada = 0.0;
            for (var observacion : datos) {
                double dx = finito(observacion.valor(x) - resultadoD.medias[x], "diferencia X");
                double dy = finito(observacion.valor(y) - resultadoD.medias[y], "diferencia Y");
                sumaCruzada = finito(sumaCruzada + finito(dx * dy, "producto cruzado"), "suma cruzada");
            }
            double producto = finito(resultadoD.sumasCuadradas[x] * resultadoD.sumasCuadradas[y],
                    "producto de sumas cuadradas");
            double denominador = finito(Math.sqrt(producto), "denominador de Pearson");
            if (denominador <= 0.0) throw new IOException("Denominador de Pearson no positivo");
            double correlacion = finito(sumaCruzada / denominador, "correlacion de Pearson");
            if (correlacion < -1.0 || correlacion > 1.0) {
                if (correlacion >= -1.0 - 1e-12 && correlacion <= 1.0 + 1e-12) {
                    correlacion = Math.max(-1.0, Math.min(1.0, correlacion));
                } else {
                    throw new IOException("Correlacion fuera de rango: " + correlacion);
                }
            }
            salida.append(formatear(correlacion)).append('\n');
        }
        publicar(salidaF, salida.toString());
    }

    private static String contenidoD(ResultadoD resultado) throws IOException {
        StringBuilder salida = new StringBuilder(
                "variable,n,media,suma_desviaciones_cuadradas,desviacion_estandar\n");
        for (int v = 0; v < VARIABLES.size(); v++) {
            double varianza = finito(resultado.sumasCuadradas[v] / resultado.cantidad, "varianza poblacional");
            double desviacion = finito(Math.sqrt(varianza), "desviacion estandar");
            salida.append(VARIABLES.get(v)).append(',').append(resultado.cantidad).append(',')
                    .append(formatear(resultado.medias[v])).append(',')
                    .append(formatear(resultado.sumasCuadradas[v])).append(',')
                    .append(formatear(desviacion)).append('\n');
        }
        return salida.toString();
    }

    private static String formatear(double valor) {
        String texto = BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).toPlainString();
        return "-0.000000".equals(texto) ? "0.000000" : texto;
    }

    private static double finito(double valor, String operacion) throws IOException {
        if (!Double.isFinite(valor)) throw new IOException("Resultado no finito en " + operacion);
        return valor;
    }

    private static void publicar(Path salida, String contenido) throws IOException {
        Path padre = salida.toAbsolutePath().getParent();
        Files.createDirectories(padre);
        Path temporal = Files.createTempFile(padre, salida.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporal, contenido, StandardCharsets.UTF_8);
            if (!Arrays.equals(contenido.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(temporal))) {
                throw new IOException("El archivo temporal no supera la validacion: " + salida);
            }
            Files.move(temporal, salida, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }
}
