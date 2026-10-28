import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;

public class PruebaTendencias {
    private static final String CABECERA = DatasetMultivariable.CABECERA + "\n";

    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("prueba-tendencias-");
        try {
            probarMuestraLineal(raiz);
            probarPendienteConocida(raiz);
            probarPendientesPequenas(raiz);
            probarUmbral();
            probarDeterminismo(raiz);
            probarEntradasInvalidas(raiz);
            probarValidacionDeG(raiz);
            System.out.println("Pendientes E y tendencias G: OK");
        } finally {
            borrar(raiz);
        }
    }

    // Misma muestra que dataset/: creciente, decreciente, constante y creciente con pendiente 2.
    private static void probarMuestraLineal(Path raiz) throws Exception {
        Path directorio = raiz.resolve("muestra");
        Path c = escribirC(directorio, "1,1.000000,6.000000,5.000000,2.000000\n"
                + "2,2.000000,5.000000,5.000000,4.000000\n"
                + "3,3.000000,4.000000,5.000000,6.000000\n"
                + "4,4.000000,3.000000,5.000000,8.000000\n"
                + "5,5.000000,2.000000,5.000000,10.000000\n"
                + "6,6.000000,1.000000,5.000000,12.000000\n");
        var resultado = ejecutarEyG(directorio, c);

        comprobarTexto(directorio.resolve("E.csv"), "variable,pendiente\n"
                + "temperatura,1.000000\n"
                + "humedad,-1.000000\n"
                + "consumo_energia,0.000000\n"
                + "demanda,2.000000\n");
        comprobarTexto(directorio.resolve("G.csv"), "variable,pendiente,tendencia\n"
                + "temperatura,1.000000,CRECIENTE\n"
                + "humedad,-1.000000,DECRECIENTE\n"
                + "consumo_energia,0.000000,ESTABLE\n"
                + "demanda,2.000000,CRECIENTE\n");
        if (resultado.pendiente(2) != 0.0) {
            throw new AssertionError("Una variable constante debe tener pendiente exactamente cero");
        }
        String huellaEsperada = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(c)));
        if (!huellaEsperada.equals(resultado.huellaC())) {
            throw new AssertionError("La huella de ResultadoE no corresponde a C.csv");
        }
        comprobarSinTemporales(directorio);
    }

    // Calculado a mano: t = 1..4, x = 1,3,2,5 -> Stx = 5.5, Stt = 5, pendiente = 1.1.
    private static void probarPendienteConocida(Path raiz) throws Exception {
        Path directorio = raiz.resolve("conocida");
        Path c = escribirC(directorio, "1,1,4,7,0\n2,3,3,7,0\n3,2,2,7,0\n4,5,1,7,0\n");
        var resultado = ejecutarEyG(directorio, c);
        comprobarCercano(resultado.pendiente(0), 1.1, "pendiente conocida de temperatura");
        comprobarCercano(resultado.pendiente(1), -1.0, "pendiente conocida de humedad");
        comprobarTexto(directorio.resolve("E.csv"), "variable,pendiente\n"
                + "temperatura,1.100000\n"
                + "humedad,-1.000000\n"
                + "consumo_energia,0.000000\n"
                + "demanda,0.000000\n");
    }

    // La presentacion redondea a 0.000000, pero la clasificacion usa el double original.
    private static void probarPendientesPequenas(Path raiz) throws Exception {
        Path directorio = raiz.resolve("pequenas");
        Path c = escribirC(directorio, "1,4e-7,-4e-7,5e-10,1\n"
                + "2,8e-7,-8e-7,1.0e-9,1\n"
                + "3,1.2e-6,-1.2e-6,1.5e-9,1\n");
        ejecutarEyG(directorio, c);
        comprobarTexto(directorio.resolve("G.csv"), "variable,pendiente,tendencia\n"
                + "temperatura,0.000000,CRECIENTE\n"
                + "humedad,0.000000,DECRECIENTE\n"
                + "consumo_energia,0.000000,ESTABLE\n"
                + "demanda,0.000000,ESTABLE\n");
    }

    private static void probarUmbral() {
        comprobarIgual(AnalisisTendencias.tendencia(AnalisisTendencias.UMBRAL), "ESTABLE");
        comprobarIgual(AnalisisTendencias.tendencia(-AnalisisTendencias.UMBRAL), "ESTABLE");
        comprobarIgual(AnalisisTendencias.tendencia(Math.nextUp(AnalisisTendencias.UMBRAL)), "CRECIENTE");
        comprobarIgual(AnalisisTendencias.tendencia(Math.nextDown(-AnalisisTendencias.UMBRAL)), "DECRECIENTE");
        comprobarIgual(AnalisisTendencias.tendencia(0.0), "ESTABLE");
        comprobarIgual(AnalisisTendencias.formatear(-0.0), "0.000000");
        comprobarIgual(AnalisisTendencias.formatear(-0.0000004), "0.000000");
        comprobarIgual(AnalisisTendencias.formatear(0.0000005), "0.000001");
        comprobarIgual(AnalisisTendencias.formatear(-2.5), "-2.500000");
    }

    private static void probarDeterminismo(Path raiz) throws Exception {
        Path datos = raiz.resolve("generado");
        GeneradorDataset.generar(datos, 1000, GeneradorDataset.SEMILLA_PREDETERMINADA);
        String contenido = DatasetMultivariable.contenido(DatasetMultivariable.leerParte(datos.resolve("parteA.csv")))
                + DatasetMultivariable.contenido(DatasetMultivariable.leerParte(datos.resolve("parteB.csv")))
                        .substring(CABECERA.length());
        Path primera = raiz.resolve("determinista-1");
        Path segunda = raiz.resolve("determinista-2");
        ejecutarEyG(primera, escribirArchivo(primera.resolve("C.csv"), contenido));
        ejecutarEyG(segunda, escribirArchivo(segunda.resolve("C.csv"), contenido));
        for (String nombre : new String[] {"E.csv", "G.csv"}) {
            if (!Arrays.equals(Files.readAllBytes(primera.resolve(nombre)),
                    Files.readAllBytes(segunda.resolve(nombre)))) {
                throw new AssertionError(nombre + " no es determinista");
            }
            var lineas = Files.readAllLines(primera.resolve(nombre), StandardCharsets.UTF_8);
            if (lineas.size() != 5) {
                throw new AssertionError(nombre + " debe tener cabecera y cuatro variables");
            }
            for (int v = 0; v < DatasetMultivariable.VARIABLES.length; v++) {
                if (!lineas.get(v + 1).startsWith(DatasetMultivariable.VARIABLES[v] + ",")) {
                    throw new AssertionError(nombre + " no respeta el orden canonico de variables");
                }
            }
        }
    }

    private static void probarEntradasInvalidas(Path raiz) throws IOException {
        rechazarE(raiz, "menos-de-tres", CABECERA + "1,1,2,3,4\n2,2,3,4,5\n");
        rechazarE(raiz, "cabecera", "otra,cabecera\n1,1,2,3,4\n2,2,3,4,5\n3,3,4,5,6\n");
        rechazarE(raiz, "no-numerico", CABECERA + "1,1,2,3,4\n2,abc,3,4,5\n3,3,4,5,6\n");
        rechazarE(raiz, "desordenado", CABECERA + "1,1,2,3,4\n3,2,3,4,5\n2,3,4,5,6\n");
        rechazarE(raiz, "no-finito", CABECERA + "1,1,2,3,4\n2,1e9999,3,4,5\n3,3,4,5,6\n");
        rechazarE(raiz, "columnas", CABECERA + "1,1,2,3\n2,2,3,4\n3,3,4,5\n");
        rechazarE(raiz, "overflow", CABECERA + "1,1e308,2,3,4\n2,1e308,3,4,5\n3,1e308,4,5,6\n");

        Path ausente = raiz.resolve("ausente");
        Files.createDirectories(ausente);
        try {
            AnalisisTendencias.calcularPendientes(ausente.resolve("C.csv"), ausente.resolve("E.csv"));
            throw new AssertionError("C.csv ausente fue aceptado");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
        comprobarNoExiste(ausente.resolve("E.csv"));

        // Un destino ocupado por un directorio no vacio hace fallar la publicacion.
        Path bloqueado = raiz.resolve("publicacion-fallida");
        Path c = escribirC(bloqueado, "1,1,2,3,4\n2,2,3,4,5\n3,3,4,5,6\n");
        escribirArchivo(bloqueado.resolve("E.csv").resolve("ocupado.txt"), "x");
        try {
            AnalisisTendencias.calcularPendientes(c, bloqueado.resolve("E.csv"));
            throw new AssertionError("E reporto exito aunque no pudo publicar");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
        comprobarSinTemporales(bloqueado);
    }

    private static void probarValidacionDeG(Path raiz) throws Exception {
        Path directorio = raiz.resolve("validacion-g");
        Path c = escribirC(directorio, "1,1,6,5,2\n2,2,5,5,4\n3,3,4,5,6\n");
        Path e = directorio.resolve("E.csv");
        Path g = directorio.resolve("G.csv");
        var resultado = AnalisisTendencias.calcularPendientes(c, e);
        String original = Files.readString(e, StandardCharsets.UTF_8);

        rechazarG(e, null, g, original, "ResultadoE ausente");
        rechazarG(e, resultado, g, original.replace("temperatura,1.000000", "temperatura,1.000001"),
                "valor alterado");
        rechazarG(e, resultado, g, original.substring(0, original.lastIndexOf("demanda")), "fila faltante");
        rechazarG(e, resultado, g, original + "demanda,2.000000\n", "fila duplicada");
        rechazarG(e, resultado, g, original.replace("humedad", "HUMEDAD"), "variable alterada");
        rechazarG(e, resultado, g, original.replace("\n", "\r\n"), "terminadores CRLF");
        rechazarG(e, resultado, g, "﻿" + original, "BOM");

        Path otro = raiz.resolve("validacion-g-otro");
        var otroResultado = AnalisisTendencias.calcularPendientes(
                escribirC(otro, "1,1,1,1,1\n2,1,1,1,1\n3,1,1,1,1\n"), otro.resolve("E.csv"));
        rechazarG(e, otroResultado, g, original, "ResultadoE de otra ejecucion");

        Files.delete(e);
        try {
            AnalisisTendencias.clasificar(e, resultado, g);
            throw new AssertionError("G acepto E.csv ausente");
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
        comprobarNoExiste(g);
        comprobarSinTemporales(directorio);
    }

    private static AnalisisTendencias.ResultadoE ejecutarEyG(Path directorio, Path c) throws IOException {
        var resultado = AnalisisTendencias.calcularPendientes(c, directorio.resolve("E.csv"));
        AnalisisTendencias.clasificar(directorio.resolve("E.csv"), resultado, directorio.resolve("G.csv"));
        return resultado;
    }

    private static void rechazarE(Path raiz, String nombre, String contenido) throws IOException {
        Path directorio = raiz.resolve("invalido-" + nombre);
        Path c = escribirArchivo(directorio.resolve("C.csv"), contenido);
        try {
            AnalisisTendencias.calcularPendientes(c, directorio.resolve("E.csv"));
            throw new AssertionError("Entrada invalida aceptada por E: " + nombre);
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
        comprobarNoExiste(directorio.resolve("E.csv"));
        comprobarSinTemporales(directorio);
    }

    private static void rechazarG(Path e, AnalisisTendencias.ResultadoE resultado, Path g,
            String contenidoE, String caso) throws IOException {
        Files.writeString(e, contenidoE, StandardCharsets.UTF_8);
        try {
            AnalisisTendencias.clasificar(e, resultado, g);
            throw new AssertionError("G acepto una entrada invalida: " + caso);
        } catch (IOException esperado) {
            // El rechazo es el resultado esperado.
        }
        comprobarNoExiste(g);
    }

    private static Path escribirC(Path directorio, String filas) throws IOException {
        return escribirArchivo(directorio.resolve("C.csv"), CABECERA + filas);
    }

    private static Path escribirArchivo(Path archivo, String contenido) throws IOException {
        Files.createDirectories(archivo.getParent());
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        return archivo;
    }

    private static void comprobarTexto(Path archivo, String esperado) throws IOException {
        if (!Arrays.equals(esperado.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(archivo))) {
            throw new AssertionError("Contenido inesperado en " + archivo.getFileName() + ":\n"
                    + Files.readString(archivo, StandardCharsets.UTF_8));
        }
    }

    private static void comprobarCercano(double obtenido, double esperado, String descripcion) {
        if (Math.abs(obtenido - esperado) > 1e-12) {
            throw new AssertionError(descripcion + ": se esperaba " + esperado + " y se obtuvo " + obtenido);
        }
    }

    private static void comprobarIgual(String obtenido, String esperado) {
        if (!esperado.equals(obtenido)) {
            throw new AssertionError("Se esperaba " + esperado + " y se obtuvo " + obtenido);
        }
    }

    private static void comprobarNoExiste(Path archivo) {
        if (Files.exists(archivo)) {
            throw new AssertionError("Se publico un resultado tras un error: " + archivo);
        }
    }

    private static void comprobarSinTemporales(Path directorio) throws IOException {
        try (var rutas = Files.list(directorio)) {
            if (rutas.anyMatch(ruta -> ruta.getFileName().toString().endsWith(".tmp"))) {
                throw new AssertionError("Quedaron temporales en " + directorio);
            }
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
