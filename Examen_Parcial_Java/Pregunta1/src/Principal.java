import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class Principal {
    final Path directorio;
    final Path dataset;
    final List<String> registro = new ArrayList<>();
    AnalisisCorrelaciones.ResultadoD resultadoD;
    AnalisisTendencias.ResultadoE resultadoE;

    Principal(String modo) throws IOException {
        this(modo, Path.of("dataset"), Path.of("salida", modo));
    }

    Principal(String modo, Path dataset, Path directorio) throws IOException {
        this.dataset = dataset;
        this.directorio = directorio;
        Files.createDirectories(directorio);
        Files.deleteIfExists(directorio.resolve("EXITO.txt"));
        for (char n = 'A'; n <= 'H'; n++) Files.deleteIfExists(p(n));
        Files.deleteIfExists(directorio.resolve("registro.csv"));
        registro.add("nodo,evento,hilo,tiempo_monotono_ns,duracion_ns,fecha_utc");
    }

    Path p(char nodo) { return directorio.resolve(nodo + (nodo == 'H' ? ".txt" : ".csv")); }

    void nodo(char n) throws Exception {
        long inicio = System.nanoTime();
        anotar(n, "INICIO", inicio, 0);
        try {
            operar(n);
            long fin = System.nanoTime();
            anotar(n, "FIN", fin, fin - inicio);
        } catch (Exception | Error e) {
            long fin = System.nanoTime();
            anotar(n, "ERROR", fin, fin - inicio);
            throw e;
        }
    }

    void operar(char n) throws Exception {
        switch (n) {
            case 'A' -> Operaciones.particion(dataset.resolve("parteA.csv"), p(n));
            case 'B' -> Operaciones.particion(dataset.resolve("parteB.csv"), p(n));
            case 'C' -> Operaciones.unir(p('A'), p('B'), p(n));
            case 'D' -> resultadoD = AnalisisCorrelaciones.calcularEstadisticas(p('C'), p(n));
            case 'E' -> resultadoE = AnalisisTendencias.calcularPendientes(p('C'), p(n));
            case 'F' -> AnalisisCorrelaciones.calcularCorrelaciones(p('C'), p('D'), resultadoD, p(n));
            case 'G' -> AnalisisTendencias.clasificar(p('E'), resultadoE, p(n));
            case 'H' -> Operaciones.resumen(p('F'), p('G'), p(n));
            default -> throw new IllegalArgumentException("Nodo desconocido: " + n);
        }
    }

    synchronized void anotar(char n, String evento, long reloj, long duracion) {
        String linea = n + "," + evento + "," + Thread.currentThread().getName()
                + "," + reloj + "," + duracion + "," + Instant.now();
        registro.add(linea);
        System.out.println(linea);
    }

    private final class Tarea implements Runnable {
        final char nodo;
        final Tarea[] anteriores;
        final Thread hilo;
        Throwable fallo;
        boolean exito;

        Tarea(char nodo, Tarea... anteriores) {
            this.nodo = nodo;
            this.anteriores = anteriores;
            this.hilo = new Thread(this, String.valueOf(nodo));
        }

        public void run() {
            try {
                for (Tarea anterior : anteriores) {
                    anterior.hilo.join();
                    if (!anterior.exito) return;
                }
                nodo(nodo);
                exito = true;
            } catch (Throwable e) {
                fallo = e;
            }
        }
    }

    private void ejecutarHilos() throws Exception {
        Tarea a = new Tarea('A'), b = new Tarea('B');
        Tarea c = new Tarea('C', a, b);
        Tarea d = new Tarea('D', c), e = new Tarea('E', c);
        Tarea f = new Tarea('F', d), g = new Tarea('G', e);
        Tarea h = new Tarea('H', f, g);
        Tarea[] tareas = {a, b, c, d, e, f, g, h};
        // Se inician todos antes de esperar: las ramas independientes pueden solaparse.
        for (Tarea tarea : tareas) tarea.hilo.start();
        InterruptedException interrupcion = null;
        for (Tarea tarea : tareas) {
            boolean terminado = false;
            while (!terminado) {
                try {
                    tarea.hilo.join();
                    terminado = true;
                } catch (InterruptedException error) {
                    if (interrupcion == null) interrupcion = error;
                    for (Tarea pendiente : tareas) pendiente.hilo.interrupt();
                }
            }
        }
        if (interrupcion != null) {
            Thread.currentThread().interrupt();
            throw interrupcion;
        }
        for (Tarea tarea : tareas) {
            if (tarea.fallo != null) throw new IOException("Fallo del nodo " + tarea.nodo, tarea.fallo);
        }
        for (Tarea tarea : tareas) {
            if (!tarea.exito) throw new IOException("Nodo incompleto: " + tarea.nodo);
        }
    }

    long ejecutar(boolean paralelo) throws Exception {
        long inicio = System.nanoTime();
        try {
            if (paralelo) ejecutarHilos();
            else for (char n = 'A'; n <= 'H'; n++) nodo(n);
        } catch (Exception | Error error) {
            try { guardarRegistro(); } catch (IOException secundario) { error.addSuppressed(secundario); }
            throw error;
        }
        long total = System.nanoTime() - inicio;
        guardarRegistro();
        Operaciones.publicar(directorio.resolve("EXITO.txt"),
                Operaciones.sha(p('H')) + "\nTiempo ns=" + total + "\n");
        return total;
    }

    private void guardarRegistro() throws IOException {
        Operaciones.publicar(directorio.resolve("registro.csv"), String.join("\n", registro) + "\n");
    }

    static void comparar(Principal serial, Principal paralelo) throws IOException {
        try {
            for (char n = 'A'; n <= 'H'; n++) {
                if (!Operaciones.sha(serial.p(n)).equals(Operaciones.sha(paralelo.p(n))))
                    throw new IOException("Diferencia nodo " + n);
            }
        } catch (IOException error) {
            Files.deleteIfExists(serial.directorio.resolve("EXITO.txt"));
            Files.deleteIfExists(paralelo.directorio.resolve("EXITO.txt"));
            throw error;
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) System.out.println("Elegir modo: serial | paralelo | comparar");
            String modo = args.length > 0 ? args[0] : new Scanner(System.in).nextLine().trim();
            if (modo.equals("generar")) {
                GeneradorDataset.generar(Path.of("dataset"),
                        args.length > 1 ? Long.parseLong(args[1])
                                : Long.getLong("pcp.observaciones", GeneradorDataset.OBSERVACIONES_PREDETERMINADAS),
                        Long.getLong("pcp.semilla", GeneradorDataset.SEMILLA_PREDETERMINADA));
                return;
            }
            if (!Set.of("serial", "paralelo", "comparar").contains(modo))
                throw new IllegalArgumentException("Uso: generar [observaciones] | serial | paralelo | comparar");
            if (modo.equals("comparar")) {
                Principal serial = new Principal("serial"), paralelo = new Principal("paralelo");
                long s = serial.ejecutar(false), p = paralelo.ejecutar(true);
                comparar(serial, paralelo);
                System.out.printf(Locale.ROOT,
                        "Tiempo serial: %.3f ms%nTiempo paralelo: %.3f ms%nSpeedup: %.3f%nValidacion SHA-256 A-H: OK%n",
                        s / 1e6, p / 1e6, p > 0 ? (double) s / p : 0);
            } else {
                System.out.printf(Locale.ROOT, "Tiempo total: %.3f ms%n",
                        new Principal(modo).ejecutar(modo.equals("paralelo")) / 1e6);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
