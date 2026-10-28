import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Coordinador {

    static final class Conexion implements AutoCloseable {
        final Socket socket;
        final DataInputStream in;
        final DataOutputStream out;
        final Configuracion.Nodo nodo;
        final long disponible;

        Conexion(Socket s, Configuracion c, Set<String> vistos) throws IOException {
            this.socket = s;
            this.in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            this.out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

            if (!in.readUTF().equals("BANCO1")) {
                throw new IOException("Protocolo invalido");
            }
            String id = in.readUTF();
            String ip = in.readUTF();

            this.nodo = c.nodos.stream()
                    .filter(n -> n.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Nodo desconocido"));

            if (!nodo.ip().equals(ip) || !s.getInetAddress().equals(Configuracion.ip(ip)) || !vistos.add(id)) {
                throw new IOException("IP incorrecta o nodo duplicado");
            }

            this.disponible = Math.min(nodo.bytes(), in.readLong());
            if (this.disponible < 1024) {
                throw new IOException("Sin espacio");
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    static long[] inspeccionar(Path datos) throws IOException {
        long n = 0, max = 1, previo = 0;
        try (var b = Files.newBufferedReader(datos, StandardCharsets.UTF_8)) {
            if (!Procesamiento.CABECERA.equals(b.readLine())) {
                throw new IOException("Cabecera invalida");
            }
            String s;
            while ((s = b.readLine()) != null) {
                long id = Long.parseLong(s.split(",", 2)[0]);
                if (id <= previo) throw new IOException("IDs repetidos o desordenados");
                previo = id;
                n++;
                max = Math.max(max, s.getBytes(StandardCharsets.UTF_8).length + 1L);
            }
        }
        return new long[]{n, max};
    }

    static List<Path> particionar(Path datos, Path dir, List<Conexion> conexiones, long[] cuotas) throws IOException {
        List<Path> partes = new ArrayList<>();
        try (var b = Files.newBufferedReader(datos, StandardCharsets.UTF_8)) {
            b.readLine(); // Cabecera
            for (int i = 0; i < conexiones.size(); i++) {
                Path parte = dir.resolve(conexiones.get(i).nodo.id() + "-entrada.csv");
                partes.add(parte);
                try (var w = Files.newBufferedWriter(parte, StandardCharsets.UTF_8)) {
                    w.write(Procesamiento.CABECERA + "\n");
                    for (long k = 0; k < cuotas[i]; k++) {
                        String s = b.readLine();
                        if (s == null) throw new EOFException("Dataset modificado");
                        w.write(s + "\n");
                    }
                }
            }
            if (b.readLine() != null) {
                throw new IOException("Dataset modificado durante particionamiento");
            }
        }
        return partes;
    }

    static void esperarHilos(Thread[] hilos, List<Conexion> conexiones) throws InterruptedException {
        boolean interrumpido = false;
        try {
            for (Thread h : hilos) {
                if (h == null) continue;
                while (h.isAlive()) {
                    try {
                        h.join();
                    } catch (InterruptedException e) {
                        interrumpido = true;
                        // Solicita interrupcion a todos los hilos trabajadores
                        for (Thread w : hilos) {
                            if (w != null) {
                                w.interrupt();
                            }
                        }
                        // Cierra los sockets para desbloquear operaciones TCP que esten esperando datos
                        for (var con : conexiones) {
                            try {
                                con.close();
                            } catch (IOException ignored) {}
                        }
                    }
                }
            }
            if (interrumpido) {
                throw new InterruptedException("Coordinador interrumpido durante la espera de trabajadores");
            }
        } finally {
            if (interrumpido) {
                Thread.currentThread().interrupt(); // Restaura el estado de interrupcion del coordinador
            }
        }
    }

    static void distribuir(Configuracion c, Path datos, Path salida) throws Exception {
        Files.createDirectories(salida);
        Files.deleteIfExists(salida.resolve("EXITO.txt"));
        Files.deleteIfExists(salida.resolve("consolidado.csv"));

        long inicio = System.nanoTime();
        long[] info = inspeccionar(datos);
        String job = UUID.randomUUID().toString();
        Path dir = salida.resolve(job);
        Files.createDirectory(dir);

        List<Conexion> conexiones = new ArrayList<>();
        try (ServerSocket servidor = new ServerSocket()) {
            servidor.setReuseAddress(true);
            servidor.bind(new InetSocketAddress(Configuracion.ip(c.texto("maestro.ip")), c.puerto()));
            System.out.println("LISTO maestro=" + c.texto("maestro.ip") + ":" + c.puerto());

            long limite = System.nanoTime() + c.numero("espera.ms", 100, 600000) * 1000000L;
            Set<String> vistos = new HashSet<>();
            while (conexiones.size() < c.nodos.size() && System.nanoTime() < limite) {
                servidor.setSoTimeout((int) Math.max(1, (limite - System.nanoTime()) / 1000000));
                try {
                    Socket s = servidor.accept();
                    try {
                        s.setSoTimeout((int) Math.max(1, (limite - System.nanoTime()) / 1000000));
                        Conexion x = new Conexion(s, c, vistos);
                        s.setSoTimeout(c.numero("socket.ms", 100, 3600000));
                        conexiones.add(x);
                    } catch (Exception e) {
                        s.close();
                        System.err.println("Conexion rechazada: " + e);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }

            if (conexiones.isEmpty()) {
                throw new IOException("No hay trabajadores disponibles");
            }

            conexiones.sort(Comparator.comparing(x -> x.nodo.id()));
            int n = conexiones.size();
            int[] pesos = new int[n];
            long[] limites = new long[n];
            for (int i = 0; i < n; i++) {
                var x = conexiones.get(i);
                pesos[i] = x.nodo.capacidad();
                limites[i] = Math.max(0, (x.disponible / 3 - 1024) / info[1]);
            }

            long[] cuotas = Balanceador.repartir(info[0], pesos, limites);
            List<Path> partes = particionar(datos, dir, conexiones, cuotas);

            List<String> carga = Collections.synchronizedList(new ArrayList<>());
            carga.add("nodo,ip,capacidad,disponible_bytes,registros,particion_bytes,procesamiento_ns");

            Thread[] hilos = new Thread[n];
            Procesamiento.Resumen[] resultados = new Procesamiento.Resumen[n];
            Throwable[] errores = new Throwable[n];

            for (int i = 0; i < n; i++) {
                final int idx = i;
                var x = conexiones.get(idx);
                System.out.println("Nodo " + x.nodo.id() + " IP=" + x.nodo.ip() + " capacidad=" + pesos[idx] + " registros=" + cuotas[idx]);

                hilos[idx] = new Thread(() -> {
                    try {
                        x.out.writeUTF(job);
                        x.out.writeLong(cuotas[idx]);
                        Transferencia.enviar(x.out, partes.get(idx));

                        long recibidos = x.in.readLong();
                        long tiempo = x.in.readLong();
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        if (recibidos != cuotas[idx] || tiempo < 0) {
                            throw new IOException("Trabajo incompleto " + x.nodo.id());
                        }

                        Path parcial = dir.resolve(x.nodo.id() + "-resultado.csv");
                        Transferencia.recibir(x.in, parcial, x.disponible / 3);

                        var r = Procesamiento.leerResumen(parcial);
                        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        if (r.cantidad() != cuotas[idx]) {
                            throw new IOException("Parcial inconsistente");
                        }

                        carga.add(x.nodo.id() + "," + x.nodo.ip() + "," + x.nodo.capacidad() + "," +
                                x.disponible + "," + cuotas[idx] + "," + Files.size(partes.get(idx)) + "," + tiempo);

                        x.out.writeUTF("OK");
                        x.out.flush();
                        resultados[idx] = r;
                    } catch (Throwable t) {
                        errores[idx] = t;
                    }
                }, "worker-" + x.nodo.id());
                hilos[idx].start();
            }

            esperarHilos(hilos, conexiones);

            for (int i = 0; i < n; i++) {
                if (errores[i] != null) {
                    if (errores[i] instanceof Exception e) throw e;
                    throw new RuntimeException("Error en trabajador " + conexiones.get(i).nodo.id(), errores[i]);
                }
                if (resultados[i] == null) {
                    throw new IOException("Resultado ausente para el nodo " + conexiones.get(i).nodo.id());
                }
            }

            var total = new Procesamiento.Resumen();
            for (var r : resultados) {
                total.unir(r);
            }

            if (total.cantidad() != info[0]) {
                throw new IOException("Cantidad global incorrecta");
            }

            total.guardar(salida.resolve("consolidado.csv"));
            long duracion = System.nanoTime() - inicio;
            Files.write(salida.resolve("carga.csv"), carga);
            Files.writeString(salida.resolve("tiempo-ns.txt"), Long.toString(duracion));
            Files.writeString(salida.resolve("EXITO.txt"),
                    "Trabajo=" + job + "\nEsperados=" + info[0] + "\nProcesados=" + total.cantidad() +
                    "\nSHA256=" + Transferencia.sha(salida.resolve("consolidado.csv")) + "\n");
            System.out.printf(Locale.ROOT, "Registros esperados/procesados: %d/%d%nTiempo distribuido: %.3f ms%n",
                    info[0], total.cantidad(), duracion / 1e6);
        } finally {
            for (var x : conexiones) {
                try {
                    x.close();
                } catch (IOException e) {
                    System.err.println(e);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            switch (args[0]) {
                case "serial" -> {
                    Path salida = Path.of(args[2]);
                    Files.createDirectories(salida);
                    Files.deleteIfExists(salida.resolve("SERIAL-EXITO.txt"));
                    long t = System.nanoTime();
                    var r = Procesamiento.procesar(Path.of(args[1]));
                    r.guardar(salida.resolve("serial.csv"));
                    long ns = System.nanoTime() - t;
                    Files.writeString(salida.resolve("serial-tiempo-ns.txt"), Long.toString(ns));
                    Files.writeString(salida.resolve("SERIAL-EXITO.txt"), "Registros=" + r.cantidad() + "\n");
                    System.out.printf(Locale.ROOT, "Registros: %d; tiempo serial: %.3f ms%n", r.cantidad(), ns / 1e6);
                }
                case "maestro" -> distribuir(new Configuracion(Path.of(args[1])), Path.of(args[2]), Path.of(args[3]));
                case "comparar" -> {
                    Path p = Path.of(args[1]);
                    if (!Files.exists(p.resolve("EXITO.txt")) || !Files.exists(p.resolve("SERIAL-EXITO.txt"))) {
                        throw new IOException("Falta ejecucion exitosa");
                    }
                    if (!Transferencia.sha(p.resolve("serial.csv")).equals(Transferencia.sha(p.resolve("consolidado.csv")))) {
                        throw new IOException("Resultados diferentes");
                    }
                    long s = Long.parseLong(Files.readString(p.resolve("serial-tiempo-ns.txt")));
                    long d = Long.parseLong(Files.readString(p.resolve("tiempo-ns.txt")));
                    System.out.printf(Locale.ROOT, "Validacion SHA-256: OK%nTiempo serial: %.3f ms%nTiempo distribuido: %.3f ms%nSpeedup: %.4f%n",
                            s / 1e6, d / 1e6, d > 0 ? (double) s / d : 0);
                }
                case "ip" -> {
                    String ip = args.length > 2 ? args[2] : InetAddress.getLocalHost().getHostAddress();
                    Configuracion.local(ip);
                    Files.writeString(Path.of(args[1]), ip + "\n");
                    System.out.println("IP guardada: " + ip);
                }
                default -> throw new IllegalArgumentException("Modos: serial dataset carpeta | maestro config dataset carpeta | comparar carpeta | ip archivo [IPv4]");
            }
        } catch (Exception e) {
            System.err.println("ERROR coordinador: " + e);
            System.exit(1);
        }
    }
}
