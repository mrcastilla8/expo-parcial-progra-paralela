import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class Operaciones {
    private Operaciones() {}

    static void publicar(Path salida, String contenido) throws IOException {
        Path temporal = Files.createTempFile(salida.toAbsolutePath().getParent(),
                salida.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporal, contenido, StandardCharsets.UTF_8);
            Files.move(temporal, salida, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    static void particion(Path entrada, Path salida) throws IOException {
        publicar(salida, DatasetMultivariable.contenido(DatasetMultivariable.leerParte(entrada)));
    }

    static void unir(Path a, Path b, Path salida) throws IOException {
        var datos = DatasetMultivariable.unir(DatasetMultivariable.leerParte(a),
                DatasetMultivariable.leerParte(b));
        StringBuilder contenido = new StringBuilder(DatasetMultivariable.CABECERA).append('\n');
        for (var fila : datos) contenido.append(fila.linea()).append('\n');
        publicar(salida, contenido.toString());
    }

    static void resumen(Path f, Path g, Path salida) throws IOException {
        List<String> correlaciones = Files.readAllLines(f, StandardCharsets.UTF_8);
        List<String> tendencias = Files.readAllLines(g, StandardCharsets.UTF_8);
        if (correlaciones.size() != 7 || tendencias.size() != 5
                || !correlaciones.get(0).equals("variable_1,variable_2,pearson")
                || !tendencias.get(0).equals("variable,pendiente,tendencia")) {
            throw new IOException("F/G: cabecera o cardinalidad invalida");
        }
        String[] variables = DatasetMultivariable.VARIABLES;
        StringBuilder contenido = new StringBuilder("CORRELACIONES\n");
        int fila = 1;
        for (int x = 0; x < 4; x++) for (int y = x + 1; y < 4; y++) {
            String[] campos = correlaciones.get(fila++).split(",", -1);
            if (campos.length != 3 || !campos[0].equals(variables[x])
                    || !campos[1].equals(variables[y])) throw new IOException("Par invalido en F");
            if (!campos[2].equals("NO_DEFINIDA") && Math.abs(decimal(campos[2])) > 1)
                throw new IOException("Pearson fuera de rango");
            contenido.append(campos[0]).append('|').append(campos[1]).append('=')
                    .append(campos[2]).append('\n');
        }
        contenido.append("TENDENCIAS\n");
        for (int i = 0; i < 4; i++) {
            String[] campos = tendencias.get(i + 1).split(",", -1);
            if (campos.length != 3 || !campos[0].equals(variables[i])
                    || !Set.of("CRECIENTE", "DECRECIENTE", "ESTABLE").contains(campos[2]))
                throw new IOException("Tendencia invalida en G");
            decimal(campos[1]);
            contenido.append(String.join("|", campos)).append('\n');
        }
        publicar(salida, contenido.toString());
    }

    private static double decimal(String valor) throws IOException {
        if (!valor.matches("-?[0-9]+\\.[0-9]{6}") || valor.equals("-0.000000"))
            throw new IOException("Formato decimal invalido: " + valor);
        double numero = Double.parseDouble(valor);
        if (!Double.isFinite(numero)) throw new IOException("Decimal no finito");
        return numero;
    }

    static String sha(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var entrada = Files.newInputStream(archivo)) {
                byte[] bloque = new byte[65536];
                int n;
                while ((n = entrada.read(bloque)) != -1) digest.update(bloque, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
