import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class AnalisisCorrelaciones {
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

        public long cantidad() {
            return cantidad;
        }

        public String huellaC() {
            return huellaC;
        }

        public double media(int variable) {
            return medias[variable];
        }

        public double sumaCuadrada(int variable) {
            return sumasCuadradas[variable];
        }
    }

    public static ResultadoD calcularEstadisticas(Path entradaC, Path salidaD) throws IOException {
        DatasetMultivariable.Documento documento = DatasetMultivariable.leerCompletoConHuella(entradaC);
        List<DatasetMultivariable.Observacion> datos = documento.observaciones();
        double[] sumas = new double[DatasetMultivariable.VARIABLES.length];
        double[] primeros = new double[DatasetMultivariable.VARIABLES.length];
        boolean[] constantes = new boolean[DatasetMultivariable.VARIABLES.length];
        java.util.Arrays.fill(constantes, true);
        for (int variable = 0; variable < primeros.length; variable++) {
            primeros[variable] = datos.get(0).valor(variable);
        }
        for (DatasetMultivariable.Observacion observacion : datos) {
            for (int variable = 0; variable < sumas.length; variable++) {
                sumas[variable] = finito(sumas[variable] + observacion.valor(variable), "suma");
                constantes[variable] &= observacion.valor(variable) == primeros[variable];
            }
        }

        double[] medias = new double[sumas.length];
        double[] sumasCuadradas = new double[sumas.length];
        for (int variable = 0; variable < sumas.length; variable++) {
            medias[variable] = finito(sumas[variable] / datos.size(), "media");
        }
        for (DatasetMultivariable.Observacion observacion : datos) {
            for (int variable = 0; variable < sumas.length; variable++) {
                double diferencia = finito(observacion.valor(variable) - medias[variable], "diferencia");
                double cuadrado = finito(diferencia * diferencia, "desviacion cuadratica");
                sumasCuadradas[variable] = finito(sumasCuadradas[variable] + cuadrado, "suma cuadratica");
            }
        }
        for (int variable = 0; variable < sumasCuadradas.length; variable++) {
            if (constantes[variable]) {
                sumasCuadradas[variable] = 0.0;
            }
        }

        ResultadoD resultado = new ResultadoD(datos.size(), documento.huellaSha256(), medias, sumasCuadradas);
        publicar(salidaD, contenidoD(resultado));
        return resultado;
    }

    public static void calcularCorrelaciones(
            Path entradaC, Path entradaD, ResultadoD resultadoD, Path salidaF) throws IOException {
        if (resultadoD == null) {
            throw new IOException("F requiere ResultadoD de la misma ejecucion");
        }
        DatasetMultivariable.Documento documento = DatasetMultivariable.leerCompletoConHuella(entradaC);
        List<DatasetMultivariable.Observacion> datos = documento.observaciones();
        if (datos.size() != resultadoD.cantidad || !documento.huellaSha256().equals(resultadoD.huellaC)) {
            throw new IOException("ResultadoD no corresponde al C.csv actual");
        }
        String esperadoD = contenidoD(resultadoD);
        if (!esperadoD.equals(Files.readString(entradaD, StandardCharsets.UTF_8))) {
            throw new IOException("D.csv es incompatible con ResultadoD o C.csv");
        }

        StringBuilder salida = new StringBuilder("variable_1,variable_2,pearson\n");
        for (int[] par : PARES) {
            int x = par[0];
            int y = par[1];
            salida.append(DatasetMultivariable.VARIABLES[x]).append(',')
                    .append(DatasetMultivariable.VARIABLES[y]).append(',');
            if (resultadoD.sumasCuadradas[x] == 0.0 || resultadoD.sumasCuadradas[y] == 0.0) {
                salida.append("NO_DEFINIDA\n");
                continue;
            }

            double sumaCruzada = 0.0;
            for (DatasetMultivariable.Observacion observacion : datos) {
                double dx = finito(observacion.valor(x) - resultadoD.medias[x], "diferencia X");
                double dy = finito(observacion.valor(y) - resultadoD.medias[y], "diferencia Y");
                sumaCruzada = finito(sumaCruzada + finito(dx * dy, "producto cruzado"), "suma cruzada");
            }
            double producto = finito(resultadoD.sumasCuadradas[x] * resultadoD.sumasCuadradas[y],
                    "producto de sumas cuadradas");
            double denominador = finito(Math.sqrt(producto), "denominador de Pearson");
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
        for (int variable = 0; variable < DatasetMultivariable.VARIABLES.length; variable++) {
            double desviacion = finito(
                    Math.sqrt(resultado.sumasCuadradas[variable] / resultado.cantidad),
                    "desviacion estandar");
            salida.append(DatasetMultivariable.VARIABLES[variable]).append(',')
                    .append(resultado.cantidad).append(',')
                    .append(formatear(resultado.medias[variable])).append(',')
                    .append(formatear(resultado.sumasCuadradas[variable])).append(',')
                    .append(formatear(desviacion)).append('\n');
        }
        return salida.toString();
    }

    private static String formatear(double valor) {
        String texto = BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).toPlainString();
        return "-0.000000".equals(texto) ? "0.000000" : texto;
    }

    private static double finito(double valor, String operacion) throws IOException {
        if (!Double.isFinite(valor)) {
            throw new IOException("Resultado no finito en " + operacion);
        }
        return valor;
    }

    private static void publicar(Path salida, String contenido) throws IOException {
        Path padre = salida.toAbsolutePath().getParent();
        if (padre == null) {
            throw new IOException("La salida no tiene directorio padre: " + salida);
        }
        Files.createDirectories(padre);
        Path temporal = Files.createTempFile(padre, salida.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporal, contenido, StandardCharsets.UTF_8);
            byte[] esperados = contenido.getBytes(StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(esperados, Files.readAllBytes(temporal))) {
                throw new IOException("El archivo temporal no supera la validacion: " + salida);
            }
            Files.move(temporal, salida, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

}
