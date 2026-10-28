import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class DatasetMultivariable {
    public static final String CABECERA = "instante,temperatura,humedad,consumo_energia,demanda";
    public static final String[] VARIABLES = {"temperatura", "humedad", "consumo_energia", "demanda"};
    private static final Pattern ENTERO = Pattern.compile("[0-9]+");
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private DatasetMultivariable() {}

    public static final class Observacion {
        private final long instante;
        private final double[] valores;
        private final String linea;

        private Observacion(long instante, double[] valores, String linea) {
            this.instante = instante;
            this.valores = valores.clone();
            this.linea = linea;
        }

        public long instante() {
            return instante;
        }

        public double valor(int variable) {
            return valores[variable];
        }

        public String linea() {
            return linea;
        }
    }

    public static final class Parte {
        private final List<Observacion> observaciones;

        private Parte(List<Observacion> observaciones) {
            this.observaciones = Collections.unmodifiableList(new ArrayList<>(observaciones));
        }

        public List<Observacion> observaciones() {
            return observaciones;
        }
    }

    public static final class Documento {
        private final List<Observacion> observaciones;
        private final String huellaSha256;

        private Documento(List<Observacion> observaciones, String huellaSha256) {
            this.observaciones = List.copyOf(observaciones);
            this.huellaSha256 = huellaSha256;
        }

        public List<Observacion> observaciones() {
            return observaciones;
        }

        public String huellaSha256() {
            return huellaSha256;
        }
    }

    public static Parte leerParte(Path archivo) throws IOException {
        List<Observacion> observaciones = leer(archivo);
        if (observaciones.isEmpty()) {
            throw new IOException("La parte no contiene observaciones: " + archivo);
        }
        validarSecuenciaLocal(observaciones, archivo);
        return new Parte(observaciones);
    }

    public static List<Observacion> unir(Parte parteA, Parte parteB) throws IOException {
        List<Observacion> resultado = new ArrayList<>(parteA.observaciones.size() + parteB.observaciones.size());
        resultado.addAll(parteA.observaciones);
        resultado.addAll(parteB.observaciones);
        validarSecuenciaGlobal(resultado);
        return Collections.unmodifiableList(resultado);
    }

    public static List<Observacion> leerCompleto(Path archivo) throws IOException {
        List<Observacion> observaciones = leer(archivo);
        validarSecuenciaGlobal(observaciones);
        return Collections.unmodifiableList(observaciones);
    }

    // La huella se obtiene de la misma lectura que produce las observaciones,
    // antes de decodificar UTF-8 o interpretar los terminadores de linea.
    public static Documento leerCompletoConHuella(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Observacion> observaciones = leer(archivo, digest);
            validarSecuenciaGlobal(observaciones);
            return new Documento(observaciones, HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    public static String contenido(Parte parte) {
        StringBuilder salida = new StringBuilder(CABECERA).append('\n');
        for (Observacion observacion : parte.observaciones) {
            salida.append(observacion.linea).append('\n');
        }
        return salida.toString();
    }

    private static List<Observacion> leer(Path archivo) throws IOException {
        return leer(archivo, null);
    }

    private static List<Observacion> leer(Path archivo, MessageDigest digest) throws IOException {
        List<Observacion> observaciones = new ArrayList<>();
        InputStream entrada = new SaltosValidosInputStream(Files.newInputStream(archivo));
        if (digest != null) {
            entrada = new DigestInputStream(entrada, digest);
        }
        var decodificador = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, decodificador))) {
            String cabecera = lector.readLine();
            if (cabecera == null) {
                throw new IOException("Archivo vacio: " + archivo);
            }
            if (!cabecera.isEmpty() && cabecera.charAt(0) == '\ufeff') {
                throw new IOException("No se admite BOM UTF-8: " + archivo);
            }
            if (!CABECERA.equals(cabecera)) {
                throw new IOException("Cabecera invalida en " + archivo);
            }

            String linea;
            int numeroLinea = 1;
            while ((linea = lector.readLine()) != null) {
                numeroLinea++;
                observaciones.add(parsear(linea, archivo, numeroLinea));
            }
        }
        return observaciones;
    }

    private static final class SaltosValidosInputStream extends FilterInputStream {
        private boolean retornoPendiente;

        private SaltosValidosInputStream(InputStream entrada) {
            super(entrada);
        }

        @Override
        public int read() throws IOException {
            int valor = super.read();
            validar(valor);
            return valor;
        }

        @Override
        public int read(byte[] buffer, int desplazamiento, int longitud) throws IOException {
            int leidos = super.read(buffer, desplazamiento, longitud);
            if (leidos < 0) {
                validar(-1);
                return -1;
            }
            for (int i = desplazamiento; i < desplazamiento + leidos; i++) {
                validar(buffer[i] & 0xff);
            }
            return leidos;
        }

        private void validar(int valor) throws IOException {
            if (retornoPendiente && valor != '\n') {
                throw new IOException("Terminador CR aislado; solo se admite LF o CRLF");
            }
            retornoPendiente = valor == '\r';
            if (valor < 0 && retornoPendiente) {
                throw new IOException("Terminador CR aislado al final del archivo");
            }
        }
    }

    private static Observacion parsear(String linea, Path archivo, int numeroLinea) throws IOException {
        if (linea.isEmpty()) {
            throw error(archivo, numeroLinea, "linea vacia");
        }
        String[] campos = linea.split(",", -1);
        if (campos.length != 5) {
            throw error(archivo, numeroLinea, "se esperaban cinco columnas");
        }
        for (String campo : campos) {
            if (campo.isEmpty() || !campo.equals(campo.trim())) {
                throw error(archivo, numeroLinea, "campo vacio o con espacios exteriores");
            }
        }
        if (!ENTERO.matcher(campos[0]).matches()) {
            throw error(archivo, numeroLinea, "instante invalido");
        }

        long instante;
        try {
            instante = Long.parseLong(campos[0]);
        } catch (NumberFormatException e) {
            throw error(archivo, numeroLinea, "instante fuera de rango");
        }
        if (instante <= 0) {
            throw error(archivo, numeroLinea, "el instante debe ser positivo");
        }

        double[] valores = new double[VARIABLES.length];
        for (int i = 0; i < valores.length; i++) {
            String texto = campos[i + 1];
            if (!DECIMAL.matcher(texto).matches()) {
                throw error(archivo, numeroLinea, "decimal invalido en " + VARIABLES[i]);
            }
            try {
                valores[i] = Double.parseDouble(texto);
            } catch (NumberFormatException e) {
                throw error(archivo, numeroLinea, "decimal fuera de rango en " + VARIABLES[i]);
            }
            if (!Double.isFinite(valores[i])) {
                throw error(archivo, numeroLinea, "valor no finito en " + VARIABLES[i]);
            }
        }
        return new Observacion(instante, valores, linea);
    }

    private static void validarSecuenciaLocal(List<Observacion> observaciones, Path archivo) throws IOException {
        for (int i = 1; i < observaciones.size(); i++) {
            long anterior = observaciones.get(i - 1).instante;
            long actual = observaciones.get(i).instante;
            if (anterior == Long.MAX_VALUE || actual != anterior + 1) {
                throw new IOException("Instantes no consecutivos en " + archivo + ": " + anterior + " y " + actual);
            }
        }
    }

    private static void validarSecuenciaGlobal(List<Observacion> observaciones) throws IOException {
        if (observaciones.size() < 3) {
            throw new IOException("Se requieren al menos tres observaciones");
        }
        for (int i = 0; i < observaciones.size(); i++) {
            long esperado = i + 1L;
            if (observaciones.get(i).instante != esperado) {
                throw new IOException("Secuencia global invalida: se esperaba " + esperado
                        + " y se encontro " + observaciones.get(i).instante);
            }
        }
    }

    private static IOException error(Path archivo, int linea, String detalle) {
        return new IOException(archivo + ": linea " + linea + ": " + detalle);
    }
}
