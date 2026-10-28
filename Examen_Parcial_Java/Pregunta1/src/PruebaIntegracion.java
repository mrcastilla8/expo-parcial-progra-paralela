import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class PruebaIntegracion {
    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }

    private static class Controlado extends Principal {
        final char fallar;
        final CyclicBarrier ab;
        final CyclicBarrier de;

        Controlado(Path datos, Path salida, char fallar, boolean barreras) throws IOException {
            super("prueba", datos, salida);
            this.fallar = fallar;
            ab = barreras ? new CyclicBarrier(2) : null;
            de = barreras ? new CyclicBarrier(2) : null;
        }

        @Override void operar(char n) throws Exception {
            if (n == fallar) throw new IOException("Fallo inyectado " + n);
            if (ab != null && (n == 'A' || n == 'B')) ab.await(10, TimeUnit.SECONDS);
            if (de != null && (n == 'D' || n == 'E')) de.await(10, TimeUnit.SECONDS);
            super.operar(n);
        }
    }

    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("integracion-pcp-");
        try {
            Path datos = raiz.resolve("dataset");
            Files.createDirectories(datos);
            String cabecera = DatasetMultivariable.CABECERA + "\n";
            Files.writeString(datos.resolve("parteA.csv"), cabecera + "1,1,3,5,1\n");
            Files.writeString(datos.resolve("parteB.csv"), cabecera + "2,2,2,5,3\n3,3,1,5,5\n");
            Principal serial = new Controlado(datos, raiz.resolve("serial"), '\0', false);
            Principal paralelo = new Controlado(datos, raiz.resolve("paralelo"), '\0', true);
            serial.ejecutar(false);
            paralelo.ejecutar(true);
            Principal.comparar(serial, paralelo);
            exigir(DatasetMultivariable.leerCompleto(serial.p('C')).size() == 3, "C pierde filas");
            String esperado = "CORRELACIONES\n"
                    + "temperatura|humedad=-1.000000\n"
                    + "temperatura|consumo_energia=NO_DEFINIDA\n"
                    + "temperatura|demanda=1.000000\n"
                    + "humedad|consumo_energia=NO_DEFINIDA\n"
                    + "humedad|demanda=-1.000000\n"
                    + "consumo_energia|demanda=NO_DEFINIDA\n"
                    + "TENDENCIAS\n"
                    + "temperatura|1.000000|CRECIENTE\n"
                    + "humedad|-1.000000|DECRECIENTE\n"
                    + "consumo_energia|0.000000|ESTABLE\n"
                    + "demanda|2.000000|CRECIENTE\n";
            exigir(Files.readString(serial.p('H')).equals(esperado), "H difiere del resultado conocido");
            List<String> s = Files.readAllLines(serial.directorio.resolve("registro.csv"));
            for (int i = 0; i < 8; i++) {
                char nodo = (char) ('A' + i);
                exigir(s.get(1 + i * 2).startsWith(nodo + ",INICIO,main,"), "Orden serial incorrecto");
                exigir(s.get(2 + i * 2).startsWith(nodo + ",FIN,main,"), "FIN serial incorrecto");
            }
            List<String> p = Files.readAllLines(paralelo.directorio.resolve("registro.csv"));
            for (String arista : List.of("AC", "BC", "CD", "CE", "DF", "EG", "FH", "GH")) {
                int fin = evento(p, arista.charAt(0), "FIN");
                int inicio = evento(p, arista.charAt(1), "INICIO");
                exigir(fin < inicio, "Dependencia violada: " + arista);
            }
            for (char n = 'A'; n <= 'H'; n++)
                exigir(p.get(evento(p, n, "INICIO")).split(",")[2].equals("" + n), "Nombre hilo");
            for (boolean modo : new boolean[]{false, true}) for (char n = 'A'; n <= 'H'; n++) {
                Path salida = raiz.resolve("fallo-" + modo + "-" + n);
                Files.createDirectories(salida);
                Files.writeString(salida.resolve("EXITO.txt"), "anterior");
                for (char viejo = 'A'; viejo <= 'H'; viejo++)
                    Files.writeString(salida.resolve(viejo + (viejo == 'H' ? ".txt" : ".csv")), "viejo");
                Principal fallo = new Controlado(datos, salida, n, false);
                try {
                    fallo.ejecutar(modo);
                    throw new AssertionError("No propago fallo " + n);
                } catch (IOException esperadoError) {
                    exigir(!Files.exists(salida.resolve("EXITO.txt")), "Exito obsoleto");
                    exigir(!Files.exists(fallo.p(n)), "Producto fallido/obsoleto");
                    exigir(!Files.exists(fallo.p('H')), "H consumio producto fallido");
                    if (!modo) for (char posterior = n; posterior <= 'H'; posterior++)
                        exigir(!Files.exists(fallo.p(posterior)), "Sucesor serial ejecutado");
                }
            }
            // La categoria usa la pendiente original, aun cuando se presenta como cero.
            Files.writeString(datos.resolve("parteA.csv"), cabecera + "1,0,3,5,1\n");
            Files.writeString(datos.resolve("parteB.csv"), cabecera
                    + "2,0.0000004,2,5,3\n3,0.0000008,1,5,5\n");
            Principal pequeno = new Principal("prueba", datos, raiz.resolve("pequeno"));
            pequeno.ejecutar(false);
            exigir(Files.readString(pequeno.p('G')).contains("temperatura,0.000000,CRECIENTE\n"),
                    "Clasificacion perdio precision");
            // Un error de comparacion tambien invalida ambos marcadores.
            Files.writeString(paralelo.p('H'), "alterado");
            try {
                Principal.comparar(serial, paralelo);
                throw new AssertionError("Comparacion acepto diferencia");
            } catch (IOException esperadoError) {
                exigir(!Files.exists(serial.directorio.resolve("EXITO.txt")), "Marcador serial");
                exigir(!Files.exists(paralelo.directorio.resolve("EXITO.txt")), "Marcador paralelo");
            }
            System.out.println("Integracion serial/8 Thread, DAG, solapamiento con barreras, 16 fallos y SHA-256: OK");
        } finally {
            try (var rutas = Files.walk(raiz)) {
                for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) Files.delete(ruta);
            }
        }
    }

    private static int evento(List<String> lineas, char nodo, String evento) {
        for (int i = 1; i < lineas.size(); i++) if (lineas.get(i).startsWith(nodo + "," + evento + ",")) return i;
        throw new AssertionError("Evento ausente: " + nodo + " " + evento);
    }
}
