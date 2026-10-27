import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

public final class GeneradorDataset {
    public static final long SEMILLA_PREDETERMINADA = 20261027L;
    public static final long OBSERVACIONES_PREDETERMINADAS = 100L;

    private GeneradorDataset() {}

    public static void generar(Path directorio, long total, long semilla) throws IOException {
        if (total < 3) {
            throw new IllegalArgumentException("Se requieren al menos tres observaciones");
        }
        Files.createDirectories(directorio);
        Path temporalA = Files.createTempFile(directorio, "parteA-", ".tmp");
        Path temporalB = Files.createTempFile(directorio, "parteB-", ".tmp");
        Path salidaA = directorio.resolve("parteA.csv");
        Path salidaB = directorio.resolve("parteB.csv");
        long corte = total / 2;
        Random random = new Random(semilla);

        try {
            try (BufferedWriter parteA = Files.newBufferedWriter(temporalA, StandardCharsets.UTF_8);
                 BufferedWriter parteB = Files.newBufferedWriter(temporalB, StandardCharsets.UTF_8)) {
                parteA.write(DatasetMultivariable.CABECERA);
                parteA.write('\n');
                parteB.write(DatasetMultivariable.CABECERA);
                parteB.write('\n');
                for (long instante = 1; instante <= total; instante++) {
                    BufferedWriter destino = instante <= corte ? parteA : parteB;
                    destino.write(Long.toString(instante));
                    for (int variable = 0; variable < DatasetMultivariable.VARIABLES.length; variable++) {
                        destino.write(',');
                        destino.write(formatear(random.nextDouble() * 100.0));
                    }
                    destino.write('\n');
                }
            }
            DatasetMultivariable.unir(
                    DatasetMultivariable.leerParte(temporalA),
                    DatasetMultivariable.leerParte(temporalB));
            Files.move(temporalA, salidaA, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporalB, salidaB, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporalA);
            Files.deleteIfExists(temporalB);
        }
    }

    private static String formatear(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    public static void main(String[] args) {
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException("Uso: GeneradorDataset [observaciones] [directorio]");
            }
            long total = args.length >= 1
                    ? Long.parseLong(args[0])
                    : Long.getLong("pcp.observaciones", OBSERVACIONES_PREDETERMINADAS);
            long semilla = Long.getLong("pcp.semilla", SEMILLA_PREDETERMINADA);
            Path directorio = args.length == 2 ? Path.of(args[1]) : Path.of("dataset");
            generar(directorio, total, semilla);
            System.out.println("Dataset generado: " + total + " observaciones, semilla=" + semilla);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
