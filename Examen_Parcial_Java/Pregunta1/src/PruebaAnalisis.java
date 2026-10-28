import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PruebaAnalisis {
    private static final String CABECERA = DatasetMultivariable.CABECERA + "\n";
    private static final String[] VARIABLES = DatasetMultivariable.VARIABLES;
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final double TOLERANCIA = 1e-6;
    private static final Map<Character, String> DESCENDIENTES = Map.of(
            'A', "CDEFGH", 'B', "CDEFGH", 'C', "DEFGH", 'D', "FH",
            'E', "GH", 'F', "H", 'G', "H", 'H', "");

    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("prueba-analisis-");
        try {
            probarReferenciaIndependiente(raiz);
            probarEntradasAdversas(raiz);
            probarProceso(raiz);
            System.out.println("Referencia independiente, entradas adversas y codigos de salida: OK");
        } finally {
            borrar(raiz);
        }
    }

    // Compara D, E, F y G con sumas exactas en BigDecimal sobre los tokens de entrada.
    private static void probarReferenciaIndependiente(Path raiz) throws Exception {
        final int cantidad = 500;
        final int corridasParalelas = 10;
        Path datos = raiz.resolve("referencia-dataset");
        GeneradorDataset.generar(datos, cantidad, GeneradorDataset.SEMILLA_PREDETERMINADA);

        Principal serial = new Principal("serial", datos, raiz.resolve("referencia-serial"));
        serial.ejecutar(false);
        for (int i = 0; i < corridasParalelas; i++) {
            Principal paralelo = new Principal("paralelo", datos, raiz.resolve("referencia-paralelo-" + i));
            paralelo.ejecutar(true);
            Principal.comparar(serial, paralelo);
        }

        List<BigDecimal[]> filas = new ArrayList<>();
        for (String parte : new String[] {"parteA.csv", "parteB.csv"}) {
            List<String> lineas = Files.readAllLines(datos.resolve(parte), StandardCharsets.UTF_8);
            for (String linea : lineas.subList(1, lineas.size())) {
                String[] campos = linea.split(",", -1);
                BigDecimal[] fila = new BigDecimal[campos.length];
                for (int i = 0; i < campos.length; i++) {
                    fila[i] = new BigDecimal(campos[i]);
                }
                filas.add(fila);
            }
        }
        exigir(filas.size() == cantidad, "Referencia con cantidad incorrecta");

        // Formulas de un paso: S(XY) = suma(xy) - suma(x) * suma(y) / N.
        BigDecimal n = BigDecimal.valueOf(cantidad);
        int columnas = VARIABLES.length + 1;
        BigDecimal[] suma = new BigDecimal[columnas];
        BigDecimal[][] productos = new BigDecimal[columnas][columnas];
        for (int i = 0; i < columnas; i++) {
            suma[i] = BigDecimal.ZERO;
            for (int j = 0; j < columnas; j++) {
                productos[i][j] = BigDecimal.ZERO;
            }
        }
        for (BigDecimal[] fila : filas) {
            for (int i = 0; i < columnas; i++) {
                suma[i] = suma[i].add(fila[i]);
                for (int j = 0; j < columnas; j++) {
                    productos[i][j] = productos[i][j].add(fila[i].multiply(fila[j]));
                }
            }
        }
        BigDecimal[][] s = new BigDecimal[columnas][columnas];
        for (int i = 0; i < columnas; i++) {
            for (int j = 0; j < columnas; j++) {
                s[i][j] = productos[i][j].subtract(suma[i].multiply(suma[j]).divide(n, PRECISION));
            }
        }

        List<String> d = Files.readAllLines(serial.p('D'), StandardCharsets.UTF_8);
        List<String> e = Files.readAllLines(serial.p('E'), StandardCharsets.UTF_8);
        List<String> f = Files.readAllLines(serial.p('F'), StandardCharsets.UTF_8);
        List<String> g = Files.readAllLines(serial.p('G'), StandardCharsets.UTF_8);
        for (int v = 0; v < VARIABLES.length; v++) {
            int x = v + 1;
            String[] filaD = d.get(v + 1).split(",");
            exigir(filaD[0].equals(VARIABLES[v]) && filaD[1].equals(Integer.toString(cantidad)),
                    "D.csv con variable o conteo incorrecto");
            cercano(filaD[2], suma[x].divide(n, PRECISION), "media de " + VARIABLES[v]);
            cercano(filaD[3], s[x][x], "suma cuadratica de " + VARIABLES[v]);
            cercano(filaD[4], s[x][x].divide(n, PRECISION).sqrt(PRECISION), "desviacion de " + VARIABLES[v]);

            BigDecimal pendiente = s[0][x].divide(s[0][0], PRECISION);
            String[] filaE = e.get(v + 1).split(",");
            String[] filaG = g.get(v + 1).split(",");
            exigir(filaE[0].equals(VARIABLES[v]) && filaG[0].equals(VARIABLES[v]), "Orden de E/G incorrecto");
            cercano(filaE[1], pendiente, "pendiente de " + VARIABLES[v]);
            exigir(filaG[1].equals(filaE[1]), "G no repite la pendiente de E");
            String tendencia = pendiente.signum() > 0 ? "CRECIENTE" : "DECRECIENTE";
            exigir(pendiente.abs().compareTo(new BigDecimal("1e-6")) > 0,
                    "La pendiente de referencia queda cerca del umbral; cambiar el fixture");
            exigir(filaG[2].equals(tendencia), "Tendencia incorrecta para " + VARIABLES[v]);
        }
        int par = 1;
        for (int i = 0; i < VARIABLES.length; i++) {
            for (int j = i + 1; j < VARIABLES.length; j++) {
                String[] filaF = f.get(par++).split(",");
                exigir(filaF[0].equals(VARIABLES[i]) && filaF[1].equals(VARIABLES[j]), "Par de F incorrecto");
                BigDecimal r = s[i + 1][j + 1].divide(
                        s[i + 1][i + 1].multiply(s[j + 1][j + 1]).sqrt(PRECISION), PRECISION);
                cercano(filaF[2], r, "Pearson " + VARIABLES[i] + "-" + VARIABLES[j]);
            }
        }
        exigir(f.size() == 7 && d.size() == 5 && e.size() == 5 && g.size() == 5, "Cardinalidad incorrecta");
    }

    private static void probarEntradasAdversas(Path raiz) throws Exception {
        String valida = "1,1,2,3,4\n2,2,3,4,5\n";
        adversa(raiz, "parteA-ausente", null, "3,3,4,5,6\n", 'A');
        adversa(raiz, "parteB-ausente", valida, null, 'B');
        adversa(raiz, "menos-de-tres", "1,1,2,3,4\n", "2,2,3,4,5\n", 'C');
        adversa(raiz, "columnas", "1,1,2,3\n2,2,3,4\n", "3,3,4,5,6\n", 'A');
        adversa(raiz, "no-numerico", valida, "3,abc,4,5,6\n", 'B');
        adversa(raiz, "cabecera", valida, null, 'B', "otra,cabecera\n3,3,4,5,6\n");
        adversa(raiz, "desorden-local", "1,1,2,3,4\n3,2,3,4,5\n2,3,4,5,6\n", "4,4,5,6,7\n", 'A');
        adversa(raiz, "hueco-entre-partes", valida, "4,4,5,6,7\n", 'C');
        adversa(raiz, "duplicado-entre-partes", valida, "2,4,5,6,7\n3,5,6,7,8\n", 'C');
        // Valores finitos cuya suma desborda: el error ocurre dentro de D (y E en paralelo).
        adversa(raiz, "desborde", "1,1e308,2,3,4\n2,1e308,3,4,5\n", "3,1e308,4,5,6\n", 'D');
    }

    private static void adversa(Path raiz, String caso, String parteA, String parteB, char nodo)
            throws Exception {
        adversa(raiz, caso, parteA, parteB, nodo, null);
    }

    private static void adversa(Path raiz, String caso, String parteA, String parteB, char nodo,
            String parteBCompleta) throws Exception {
        Path datos = raiz.resolve("adversa-" + caso);
        Files.createDirectories(datos);
        if (parteA != null) {
            escribir(datos.resolve("parteA.csv"), CABECERA + parteA);
        }
        if (parteBCompleta != null) {
            escribir(datos.resolve("parteB.csv"), parteBCompleta);
        } else if (parteB != null) {
            escribir(datos.resolve("parteB.csv"), CABECERA + parteB);
        }

        for (boolean paralelo : new boolean[] {false, true}) {
            Path salida = datos.resolve(paralelo ? "paralelo" : "serial");
            Files.createDirectories(salida);
            escribir(salida.resolve("EXITO.txt"), "corrida anterior");
            for (char anterior = 'A'; anterior <= 'H'; anterior++) {
                escribir(salida.resolve(anterior + (anterior == 'H' ? ".txt" : ".csv")), "obsoleto");
            }
            Principal principal = new Principal("prueba", datos, salida);
            try {
                principal.ejecutar(paralelo);
                throw new AssertionError("Entrada adversa aceptada: " + caso + " paralelo=" + paralelo);
            } catch (AssertionError error) {
                throw error;
            } catch (Exception esperado) {
                // El fallo es el resultado esperado.
            }
            String descripcion = caso + " paralelo=" + paralelo;
            exigir(!Files.exists(salida.resolve("EXITO.txt")), "EXITO.txt tras fallo: " + descripcion);
            exigir(!Files.exists(principal.p(nodo)), "Producto del nodo fallido: " + descripcion);
            exigir(!Files.exists(principal.p('H')), "H publicado tras fallo: " + descripcion);

            List<String> traza = Files.readAllLines(salida.resolve("registro.csv"), StandardCharsets.UTF_8);
            exigir(traza.stream().anyMatch(linea -> linea.startsWith(nodo + ",ERROR,")),
                    "Falta ERROR del nodo " + nodo + ": " + descripcion);
            for (String linea : traza.subList(1, traza.size())) {
                if (linea.split(",")[1].equals("ERROR")) {
                    for (char sucesor : DESCENDIENTES.get(linea.charAt(0)).toCharArray()) {
                        exigir(traza.stream().noneMatch(otra -> otra.startsWith(sucesor + ",INICIO,")),
                                "Se inicio " + sucesor + " tras ERROR de " + linea.charAt(0) + ": " + descripcion);
                        exigir(!Files.exists(principal.p(sucesor)),
                                "Producto de " + sucesor + " tras ERROR: " + descripcion);
                    }
                }
            }
        }
    }

    // Ejecuta los puntos de entrada como procesos independientes para observar el codigo de salida.
    private static void probarProceso(Path raiz) throws Exception {
        Path trabajo = raiz.resolve("proceso");
        Path datos = trabajo.resolve("dataset");
        Files.createDirectories(datos);
        escribir(datos.resolve("parteA.csv"), CABECERA + "1,1,6,5,2\n2,2,5,5,4\n3,3,4,5,6\n");
        escribir(datos.resolve("parteB.csv"), CABECERA + "4,4,3,5,8\n5,5,2,5,10\n6,6,1,5,12\n");
        Path exitoSerial = trabajo.resolve("salida").resolve("serial").resolve("EXITO.txt");
        Path exitoParalelo = trabajo.resolve("salida").resolve("paralelo").resolve("EXITO.txt");

        exigir(proceso(trabajo, "comparar-exito", "Principal", "comparar") == 0, "comparar devolvio error");
        exigir(Files.exists(exitoSerial) && Files.exists(exitoParalelo), "comparar no dejo ambos EXITO.txt");
        exigir(Files.readString(trabajo.resolve("comparar-exito.log"), StandardCharsets.UTF_8)
                .contains("Validacion SHA-256 A-H: OK"), "comparar no informo la validacion");

        exigir(proceso(trabajo, "modo-invalido", "Principal", "otro") != 0, "Modo invalido devolvio 0");

        escribir(datos.resolve("parteB.csv"), CABECERA + "4,4,3,5,8\n5,abc,2,5,10\n");
        exigir(proceso(trabajo, "serial-fallo", "Serial") != 0, "Serial con error devolvio 0");
        exigir(!Files.exists(exitoSerial), "Serial con error conservo EXITO.txt");
        exigir(proceso(trabajo, "paralelo-fallo", "Paralelo") != 0, "Paralelo con error devolvio 0");
        exigir(!Files.exists(exitoParalelo), "Paralelo con error conservo EXITO.txt");

        Files.delete(datos.resolve("parteA.csv"));
        exigir(proceso(trabajo, "comparar-fallo", "Principal", "comparar") != 0,
                "comparar con entrada ausente devolvio 0");
        exigir(!Files.exists(exitoSerial) && !Files.exists(exitoParalelo),
                "comparar con entrada ausente dejo EXITO.txt");
    }

    private static int proceso(Path trabajo, String nombre, String... argumentos) throws Exception {
        List<String> comando = new ArrayList<>();
        comando.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        comando.add("-cp");
        List<String> classpath = new ArrayList<>();
        for (String ruta : System.getProperty("java.class.path").split(File.pathSeparator)) {
            classpath.add(Path.of(ruta).toAbsolutePath().toString());
        }
        comando.add(String.join(File.pathSeparator, classpath));
        comando.addAll(List.of(argumentos));
        Process proceso = new ProcessBuilder(comando)
                .directory(trabajo.toFile())
                .redirectErrorStream(true)
                .redirectOutput(trabajo.resolve(nombre + ".log").toFile())
                .start();
        if (!proceso.waitFor(60, TimeUnit.SECONDS)) {
            proceso.destroyForcibly();
            throw new AssertionError("El proceso no termino: " + nombre);
        }
        return proceso.exitValue();
    }

    private static void cercano(String publicado, BigDecimal referencia, String descripcion) {
        double diferencia = Math.abs(Double.parseDouble(publicado) - referencia.doubleValue());
        exigir(diferencia <= TOLERANCIA, descripcion + ": publicado " + publicado
                + ", referencia " + referencia.round(new MathContext(12)));
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }

    private static void escribir(Path archivo, String contenido) throws IOException {
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
    }

    private static void borrar(Path raiz) throws IOException {
        try (var rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        }
    }
}
