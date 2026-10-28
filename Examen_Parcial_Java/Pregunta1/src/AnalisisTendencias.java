import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public final class AnalisisTendencias {
    public static final String CABECERA_E = "variable,pendiente";
    public static final String CABECERA_G = "variable,pendiente,tendencia";
    public static final double UMBRAL = 1e-9;

    private AnalisisTendencias() {}

    public static final class ResultadoE {
        private final double[] pendientes;
        private final String huellaC;

        private ResultadoE(double[] pendientes, String huellaC) {
            this.pendientes = pendientes.clone();
            this.huellaC = huellaC;
        }

        public double pendiente(int variable) {
            return pendientes[variable];
        }

        public String huellaC() {
            return huellaC;
        }
    }

    // Nodo E: pendiente de minimos cuadrados de cada variable respecto de instante.
    public static ResultadoE calcularPendientes(Path c, Path e) throws IOException {
        String huella = sha256(Files.readAllBytes(c));
        List<DatasetMultivariable.Observacion> observaciones = DatasetMultivariable.leerCompleto(c);
        if (!huella.equals(sha256(Files.readAllBytes(c)))) {
            throw new IOException("C.csv cambio durante la lectura: " + c);
        }

        int n = observaciones.size();
        double sumaInstantes = 0;
        double[] sumas = new double[DatasetMultivariable.VARIABLES.length];
        for (DatasetMultivariable.Observacion observacion : observaciones) {
            sumaInstantes += observacion.instante();
            for (int v = 0; v < sumas.length; v++) {
                sumas[v] += observacion.valor(v);
            }
        }
        double mediaInstantes = finito(sumaInstantes / n, "media de instante");
        double[] medias = new double[sumas.length];
        for (int v = 0; v < sumas.length; v++) {
            medias[v] = finito(sumas[v] / n, "media de " + DatasetMultivariable.VARIABLES[v]);
        }

        double stt = 0;
        double[] stx = new double[sumas.length];
        for (DatasetMultivariable.Observacion observacion : observaciones) {
            double dt = observacion.instante() - mediaInstantes;
            stt += dt * dt;
            for (int v = 0; v < stx.length; v++) {
                stx[v] += dt * (observacion.valor(v) - medias[v]);
            }
        }
        finito(stt, "suma cuadratica de instante");
        if (stt <= 0) {
            throw new IOException("Suma cuadratica de instante no positiva");
        }

        double[] pendientes = new double[stx.length];
        for (int v = 0; v < stx.length; v++) {
            String variable = DatasetMultivariable.VARIABLES[v];
            finito(stx[v], "covarianza de " + variable);
            pendientes[v] = finito(stx[v] / stt, "pendiente de " + variable);
        }

        ResultadoE resultado = new ResultadoE(pendientes, huella);
        publicar(e, contenidoE(resultado));
        return resultado;
    }

    // Nodo G: valida E.csv contra el resultado de E y clasifica con las pendientes sin redondear.
    public static void clasificar(Path e, ResultadoE resultado, Path g) throws IOException {
        if (resultado == null) {
            throw new IOException("ResultadoE no disponible para G");
        }
        if (!contenidoE(resultado).equals(leerUtf8(e))) {
            throw new IOException("E.csv no corresponde al ResultadoE de esta ejecucion: " + e);
        }

        StringBuilder salida = new StringBuilder(CABECERA_G).append('\n');
        for (int v = 0; v < DatasetMultivariable.VARIABLES.length; v++) {
            double pendiente = resultado.pendiente(v);
            salida.append(DatasetMultivariable.VARIABLES[v]).append(',')
                    .append(formatear(pendiente)).append(',')
                    .append(tendencia(pendiente)).append('\n');
        }
        publicar(g, salida.toString());
    }

    static String tendencia(double pendiente) {
        if (pendiente > UMBRAL) {
            return "CRECIENTE";
        }
        if (pendiente < -UMBRAL) {
            return "DECRECIENTE";
        }
        return "ESTABLE";
    }

    static String formatear(double valor) {
        String texto = BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).toPlainString();
        return texto.equals("-0.000000") ? "0.000000" : texto;
    }

    private static String contenidoE(ResultadoE resultado) {
        StringBuilder salida = new StringBuilder(CABECERA_E).append('\n');
        for (int v = 0; v < DatasetMultivariable.VARIABLES.length; v++) {
            salida.append(DatasetMultivariable.VARIABLES[v]).append(',')
                    .append(formatear(resultado.pendiente(v))).append('\n');
        }
        return salida.toString();
    }

    private static double finito(double valor, String descripcion) throws IOException {
        if (!Double.isFinite(valor)) {
            throw new IOException("Resultado no finito: " + descripcion);
        }
        return valor;
    }

    private static String leerUtf8(Path archivo) throws IOException {
        var decodificador = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decodificador.decode(ByteBuffer.wrap(Files.readAllBytes(archivo))).toString();
    }

    private static void publicar(Path salida, String contenido) throws IOException {
        Path directorio = salida.toAbsolutePath().getParent();
        Files.createDirectories(directorio);
        Path temporal = Files.createTempFile(directorio, salida.getFileName().toString(), ".tmp");
        try {
            byte[] bytes = contenido.getBytes(StandardCharsets.UTF_8);
            Files.write(temporal, bytes);
            if (!Arrays.equals(bytes, Files.readAllBytes(temporal))) {
                throw new IOException("El temporal no coincide con el contenido escrito: " + temporal);
            }
            Files.move(temporal, salida, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 no disponible", ex);
        }
    }
}
