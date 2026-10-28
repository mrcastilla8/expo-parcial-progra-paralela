import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class PruebaInterrupcion {

    public static void main(String[] args) throws Exception {
        probarInterrupcionCoordinadorActivo();
        probarLimpiezaRobustaEsperarHilos();
        probarInterrupcionConTrabajadoresYaTerminados();
        System.out.println("Pruebas de interrupcion del coordinador: OK");
    }

    private static void probarInterrupcionCoordinadorActivo() throws Exception {
        Path tempDir = Files.createTempDirectory("prueba-interrupcion-");
        Path salida = tempDir.resolve("resultados");
        Path dataset = tempDir.resolve("transacciones.csv");
        Path configPath = tempDir.resolve("cluster.properties");

        try {
            // 1. Dataset de prueba
            String data = Procesamiento.CABECERA + "\n"
                    + "1,1001,2026-10-01,DEPOSITO,100.00,CENTRAL\n"
                    + "2,1001,2026-10-02,RETIRO,50.00,CENTRAL\n";
            Files.writeString(dataset, data, StandardCharsets.UTF_8);

            // 2. Puerto dinamico disponible
            int puerto;
            try (ServerSocket ss = new ServerSocket(0)) {
                puerto = ss.getLocalPort();
            }

            // 3. Configuracion
            String configText = "maestro.ip=127.0.0.1\n"
                    + "maestro.puerto=" + puerto + "\n"
                    + "espera.ms=10000\n"
                    + "socket.ms=10000\n"
                    + "nodos=nodo1\n"
                    + "nodo1.ip=127.0.0.1\n"
                    + "nodo1.capacidad=1\n"
                    + "nodo1.almacenamiento.mb=10\n";
            Files.writeString(configPath, configText, StandardCharsets.UTF_8);

            Configuracion config = new Configuracion(configPath);
            Files.createDirectories(salida);
            Files.writeString(salida.resolve("EXITO.txt"), "preexistente\n");

            AtomicReference<Throwable> errorCoordinador = new AtomicReference<>();
            AtomicBoolean interruptFlagRestored = new AtomicBoolean();

            Thread hiloCoordinador = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, dataset, salida);
                } catch (Throwable t) {
                    errorCoordinador.set(t);
                    interruptFlagRestored.set(Thread.currentThread().isInterrupted());
                }
            }, "hilo-coordinador-test");

            hiloCoordinador.start();

            // 4. Conectar cliente simulando trabajador
            Socket s = null;
            for (int intento = 0; intento < 50; intento++) {
                try {
                    s = new Socket("127.0.0.1", puerto);
                    break;
                } catch (IOException e) {
                    Thread.sleep(50);
                }
            }
            if (s == null) {
                throw new AssertionError("No se pudo conectar al maestro en el puerto " + puerto);
            }

            s.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));

            // Handshake BANCO1
            out.writeUTF("BANCO1");
            out.writeUTF("nodo1");
            out.writeUTF("127.0.0.1");
            out.writeLong(10 * 1024 * 1024L); // 10 MiB libres
            out.flush();

            // Leer recepcion inicial de tarea desde el maestro
            String jobId = in.readUTF();
            long cuota = in.readLong();
            if (cuota != 2) {
                throw new AssertionError("Cuota esperada 2 pero recibida: " + cuota);
            }

            // Verificar que el hilo trabajador del coordinador este activo
            Thread hiloWorker = null;
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if ("worker-nodo1".equals(t.getName())) {
                    hiloWorker = t;
                    break;
                }
            }
            if (hiloWorker == null || !hiloWorker.isAlive()) {
                throw new AssertionError("El hilo worker-nodo1 no se encuentra activo");
            }

            // 5. Interrumpir al coordinador mientras los trabajadores estan activos
            hiloCoordinador.interrupt();

            // Esperar que el coordinador termine
            hiloCoordinador.join(5000);
            if (hiloCoordinador.isAlive()) {
                throw new AssertionError("El hilo del coordinador no termino tras la interrupcion");
            }

            // Asercion 1: La ejecucion termina sin dejar hilos trabajadores activos
            if (hiloWorker.isAlive()) {
                throw new AssertionError("El hilo worker-nodo1 sigue activo tras la finalizacion del coordinador");
            }
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith("worker-") && t.isAlive()) {
                    throw new AssertionError("Hilo trabajador remanente activo: " + t.getName());
                }
            }

            // Asercion 2: No se produce falso exito (ni EXITO.txt ni consolidado.csv)
            if (Files.exists(salida.resolve("EXITO.txt"))) {
                throw new AssertionError("Se encontro EXITO.txt a pesar de la interrupcion");
            }
            if (Files.exists(salida.resolve("consolidado.csv"))) {
                throw new AssertionError("Se encontro consolidado.csv a pesar de la interrupcion");
            }
            if (errorCoordinador.get() == null) {
                throw new AssertionError("El coordinador debio propagar un error al ser interrumpido");
            }

            // Asercion 3: Se cierran las conexiones (socket cerrado del lado servidor)
            boolean cerrado = false;
            try {
                byte[] buffer = new byte[1024];
                int leidos;
                while ((leidos = in.read(buffer)) != -1) {
                    // Drenar bytes remanentes en buffer del SO
                }
                cerrado = true; // Alcanzo EOF (-1)
            } catch (SocketException | EOFException esperado) {
                cerrado = true; // Conexion abortada/cerrada por servidor
            } finally {
                s.close();
            }
            if (!cerrado) {
                throw new AssertionError("El socket del servidor no fue cerrado");
            }

            // Asercion 4: Restaura el estado de interrupcion del coordinador
            if (!interruptFlagRestored.get()) {
                throw new AssertionError("El estado de interrupcion del coordinador no fue restaurado");
            }

        } finally {
            borrarRecursivo(tempDir);
        }
    }

    private static void probarLimpiezaRobustaEsperarHilos() throws Exception {
        AtomicBoolean interrupcionRecibida1 = new AtomicBoolean();
        AtomicBoolean interrupcionRecibida2 = new AtomicBoolean();

        Thread w1 = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupcionRecibida1.set(true);
            }
        });
        Thread w2 = new Thread(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                interrupcionRecibida2.set(true);
            }
        });

        w1.start();
        w2.start();

        Thread[] hilos = new Thread[]{w1, w2};
        List<Coordinador.Conexion> conexionesVacias = new ArrayList<>();

        AtomicReference<Throwable> errorEsperar = new AtomicReference<>();
        AtomicBoolean flagRestaurado = new AtomicBoolean();

        Thread caller = new Thread(() -> {
            try {
                Coordinador.esperarHilos(hilos, conexionesVacias);
            } catch (InterruptedException e) {
                errorEsperar.set(e);
                flagRestaurado.set(Thread.currentThread().isInterrupted());
            }
        });

        caller.start();
        Thread.sleep(50);

        caller.interrupt();
        caller.interrupt();

        caller.join(3000);
        if (caller.isAlive()) {
            throw new AssertionError("esperarHilos no retorno tras la interrupcion");
        }

        if (w1.isAlive() || w2.isAlive()) {
            throw new AssertionError("Los hilos no terminaron al llamar esperarHilos");
        }
        if (!interrupcionRecibida1.get() || !interrupcionRecibida2.get()) {
            throw new AssertionError("No se solicito la interrupcion a los hilos trabajadores");
        }
        if (!(errorEsperar.get() instanceof InterruptedException)) {
            throw new AssertionError("esperarHilos debio lanzar InterruptedException");
        }
        if (!flagRestaurado.get()) {
            throw new AssertionError("esperarHilos debio restaurar el flag de interrupcion");
        }
    }

    private static void probarInterrupcionConTrabajadoresYaTerminados() throws Exception {
        // Caso limite: los trabajadores ya finalizaron antes de que el coordinador entre a esperarHilos(),
        // pero el hilo coordinador tiene activado su estado de interrupcion.
        Thread w1 = new Thread(() -> {});
        Thread w2 = new Thread(() -> {});
        w1.start();
        w2.start();
        w1.join();
        w2.join();

        if (w1.isAlive() || w2.isAlive()) {
            throw new AssertionError("Los trabajadores debieron haber finalizado previamente");
        }

        Thread[] hilos = new Thread[]{w1, w2};
        List<Coordinador.Conexion> conexiones = new ArrayList<>();

        AtomicReference<Throwable> errorCapturado = new AtomicReference<>();
        AtomicBoolean flagRestaurado = new AtomicBoolean();

        Thread coordinador = new Thread(() -> {
            Thread.currentThread().interrupt(); // Marca como interrumpido al hilo coordinador
            try {
                Coordinador.esperarHilos(hilos, conexiones);
            } catch (InterruptedException e) {
                errorCapturado.set(e);
                flagRestaurado.set(Thread.currentThread().isInterrupted());
            }
        });

        coordinador.start();
        coordinador.join(3000);

        if (coordinador.isAlive()) {
            throw new AssertionError("El hilo coordinador no retorno a tiempo");
        }

        // Verifica que se propague la interrupcion
        if (!(errorCapturado.get() instanceof InterruptedException)) {
            throw new AssertionError("esperarHilos debio propagar InterruptedException aun con trabajadores ya terminados");
        }

        // Verifica que el estado de interrupcion quede restaurado
        if (!flagRestaurado.get()) {
            throw new AssertionError("El estado de interrupcion del coordinador debio quedar restaurado");
        }
    }

    private static void borrarRecursivo(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (var s = Files.list(p)) {
                    for (Path h : s.toList()) borrarRecursivo(h);
                }
            }
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }
}
