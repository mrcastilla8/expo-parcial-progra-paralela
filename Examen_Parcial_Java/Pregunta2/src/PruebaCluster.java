import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class PruebaCluster {

    public static void main(String[] args) throws Exception {
        probarClusterNormal3Jvm();
        probarDisponibilidadParcial();
        probarSinTrabajadores();
        probarConfiguracionEIpInvalida();
        probarTrabajadorDesconocidoYDuplicado();
        probarCuotaCeroYRedistribucion();
        probarAlmacenamientoInsuficienteEnCoordinador();
        probarDesconexionTrabajador();
        System.out.println("Regresion del cluster y balanceo tras migrar a Thread: OK");
    }

    private static void probarClusterNormal3Jvm() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-normal-");
        Process p1 = null, p2 = null, p3 = null;
        Thread maestro = null;
        try {
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "700", "50"});

            Path salidaSerial = raiz.resolve("serial");
            Coordinador.main(new String[]{"serial", datos.toString(), salidaSerial.toString()});

            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 10000, "nodo1,nodo2,nodo3", Map.of(
                    "nodo1", new int[]{4, 10},
                    "nodo2", new int[]{2, 10},
                    "nodo3", new int[]{1, 10}
            ));
            Configuracion config = new Configuracion(configPath);

            Path ipFile = raiz.resolve("ip.txt");
            Files.writeString(ipFile, "127.0.0.1\n");

            Path salidaDist = raiz.resolve("distribuido");
            AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salidaDist);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();

            Thread.sleep(300);

            p1 = ejecutarTrabajador(configPath, "nodo1", ipFile, raiz.resolve("trabajo1"));
            p2 = ejecutarTrabajador(configPath, "nodo2", ipFile, raiz.resolve("trabajo2"));
            p3 = ejecutarTrabajador(configPath, "nodo3", ipFile, raiz.resolve("trabajo3"));

            esperarProceso(p1, "trabajador 1");
            esperarProceso(p2, "trabajador 2");
            esperarProceso(p3, "trabajador 3");

            maestro.join(5000);
            exigir(!maestro.isAlive() && errorMaestro.get() == null, "Fallo en hilo maestro: " + errorMaestro.get());

            String shaSerial = Transferencia.sha(salidaSerial.resolve("serial.csv"));
            String shaConsolidado = Transferencia.sha(salidaDist.resolve("consolidado.csv"));
            exigir(shaSerial.equals(shaConsolidado), "SHA-256 consolidado difiere del serial");
            exigir(Files.exists(salidaDist.resolve("EXITO.txt")), "Falta EXITO.txt");

            Path dirJob = buscarDirectorioJob(salidaDist);
            List<Long> todosLosIds = new ArrayList<>();
            Map<String, Integer> filasPorNodo = new HashMap<>();

            for (String id : List.of("nodo1", "nodo2", "nodo3")) {
                Path parte = dirJob.resolve(id + "-entrada.csv");
                exigir(Files.exists(parte), "Falta archivo de particion para " + id);
                List<String> lineas = Files.readAllLines(parte, StandardCharsets.UTF_8);
                exigir(Procesamiento.CABECERA.equals(lineas.get(0)), "Cabecera incorrecta en particion");
                int filas = lineas.size() - 1;
                filasPorNodo.put(id, filas);
                for (int i = 1; i < lineas.size(); i++) {
                    todosLosIds.add(Long.parseLong(lineas.get(i).split(",", 2)[0]));
                }
            }

            exigir(filasPorNodo.get("nodo1") == 400, "nodo1 debe tener 400 filas");
            exigir(filasPorNodo.get("nodo2") == 200, "nodo2 debe tener 200 filas");
            exigir(filasPorNodo.get("nodo3") == 100, "nodo3 debe tener 100 filas");

            exigir(todosLosIds.size() == 700, "Totalidad de filas incorrecta");
            for (int i = 0; i < 700; i++) {
                exigir(todosLosIds.get(i) == (long) (i + 1), "ID fuera de orden o duplicado: " + todosLosIds.get(i));
            }
        } finally {
            matarProceso(p1); matarProceso(p2); matarProceso(p3);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void probarDisponibilidadParcial() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-parcial-");
        Process p1 = null, p2 = null;
        Thread maestro = null;
        try {
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "300", "30"});

            Path salidaSerial = raiz.resolve("serial");
            Coordinador.main(new String[]{"serial", datos.toString(), salidaSerial.toString()});

            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 1500, "nodo1,nodo2,nodo3", Map.of(
                    "nodo1", new int[]{4, 10},
                    "nodo2", new int[]{2, 10},
                    "nodo3", new int[]{1, 10}
            ));
            Configuracion config = new Configuracion(configPath);
            Path ipFile = raiz.resolve("ip.txt");
            Files.writeString(ipFile, "127.0.0.1\n");

            Path salidaDist = raiz.resolve("distribuido");
            AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salidaDist);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            p1 = ejecutarTrabajador(configPath, "nodo1", ipFile, raiz.resolve("trabajo1"));
            p2 = ejecutarTrabajador(configPath, "nodo2", ipFile, raiz.resolve("trabajo2"));

            esperarProceso(p1, "trabajador 1 parcial");
            esperarProceso(p2, "trabajador 2 parcial");

            maestro.join(10000);
            exigir(!maestro.isAlive() && errorMaestro.get() == null, "Maestro no tolero disponibilidad parcial: " + errorMaestro.get());

            String shaSerial = Transferencia.sha(salidaSerial.resolve("serial.csv"));
            String shaConsolidado = Transferencia.sha(salidaDist.resolve("consolidado.csv"));
            exigir(shaSerial.equals(shaConsolidado), "SHA-256 no coincide en disponibilidad parcial");
        } finally {
            matarProceso(p1); matarProceso(p2);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void probarSinTrabajadores() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-sin-workers-");
        try {
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "100", "10"});
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 300, "nodo1", Map.of("nodo1", new int[]{1, 10}));
            Configuracion config = new Configuracion(configPath);

            Path salida = raiz.resolve("salida");
            Files.createDirectories(salida);
            Files.writeString(salida.resolve("EXITO.txt"), "preexistente\n");

            boolean fallo = false;
            try {
                Coordinador.distribuir(config, datos, salida);
            } catch (IOException e) {
                fallo = true;
            }
            exigir(fallo, "Coordinador debio fallar ante ausencia de trabajadores");
            exigir(!Files.exists(salida.resolve("EXITO.txt")), "EXITO.txt no debe existir tras fallo");
            exigir(!Files.exists(salida.resolve("consolidado.csv")), "consolidado.csv no debe existir");
        } finally {
            borrar(raiz);
        }
    }

    private static void probarConfiguracionEIpInvalida() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-cfg-invalida-");
        try {
            Path cfgInvalida = raiz.resolve("invalida.properties");
            Files.writeString(cfgInvalida, "maestro.ip=999.1.1.1\nmaestro.puerto=5000\nespera.ms=1000\nsocket.ms=1000\nnodos=n1\nn1.ip=127.0.0.1\nn1.capacidad=1\nn1.almacenamiento.mb=1\n");
            try {
                new Configuracion(cfgInvalida);
                throw new AssertionError("Debio rechazar IP 999.1.1.1");
            } catch (IllegalArgumentException esperado) {}

            Files.writeString(cfgInvalida, "maestro.ip=127.0.0.1\nmaestro.puerto=5000\nespera.ms=1000\nsocket.ms=1000\nnodos=n1\nn1.ip=192.0.2.1\nn1.capacidad=1\nn1.almacenamiento.mb=1\n");
            try {
                Configuracion.local("192.0.2.1");
                throw new AssertionError("Debio rechazar IP no perteneciente al equipo");
            } catch (IOException esperado) {}
        } finally {
            borrar(raiz);
        }
    }

    private static void probarTrabajadorDesconocidoYDuplicado() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-rechazos-");
        Socket sDesc = null, s1 = null, sDup = null, s2 = null;
        Thread maestro = null;
        AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 4000, "nodo1,nodo2", Map.of(
                    "nodo1", new int[]{1, 10},
                    "nodo2", new int[]{1, 10}
            ));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "20", "5"});
            Path salida = raiz.resolve("salida");

            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salida);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            // 1. Nodo desconocido: debe ser rechazado cerrando la conexion
            sDesc = new Socket("127.0.0.1", puerto);
            sDesc.setSoTimeout(3000);
            var outDesc = new DataOutputStream(new BufferedOutputStream(sDesc.getOutputStream()));
            var inDesc = new DataInputStream(new BufferedInputStream(sDesc.getInputStream()));
            outDesc.writeUTF("BANCO1");
            outDesc.writeUTF("nodoFantasma");
            outDesc.writeUTF("127.0.0.1");
            outDesc.writeLong(10485760L);
            outDesc.flush();

            boolean rechazadoDesc = false;
            try {
                int r = inDesc.read();
                if (r == -1) rechazadoDesc = true;
            } catch (IOException esperado) {
                rechazadoDesc = true;
            }
            exigir(rechazadoDesc, "El nodo desconocido fue aceptado indebidamente");
            sDesc.close();

            // 2. Primer nodo1 valido: aceptado
            s1 = new Socket("127.0.0.1", puerto);
            s1.setSoTimeout(5000);
            var out1 = new DataOutputStream(new BufferedOutputStream(s1.getOutputStream()));
            var in1 = new DataInputStream(new BufferedInputStream(s1.getInputStream()));
            out1.writeUTF("BANCO1");
            out1.writeUTF("nodo1");
            out1.writeUTF("127.0.0.1");
            out1.writeLong(10485760L);
            out1.flush();

            // 3. Intento de nodo duplicado (nodo1) mientras la fase de admision sigue abierta (c.nodos.size == 2)
            sDup = new Socket("127.0.0.1", puerto);
            sDup.setSoTimeout(3000);
            var outDup = new DataOutputStream(new BufferedOutputStream(sDup.getOutputStream()));
            var inDup = new DataInputStream(new BufferedInputStream(sDup.getInputStream()));
            outDup.writeUTF("BANCO1");
            outDup.writeUTF("nodo1"); // Duplicado
            outDup.writeUTF("127.0.0.1");
            outDup.writeLong(10485760L);
            outDup.flush();

            boolean rechazadoDup = false;
            try {
                int r = inDup.read();
                if (r == -1) rechazadoDup = true;
            } catch (IOException esperado) {
                rechazadoDup = true;
            }
            exigir(rechazadoDup, "El nodo duplicado fue aceptado indebidamente");
            sDup.close();

            // 4. Segundo nodo valido (nodo2) para completar la admision
            s2 = new Socket("127.0.0.1", puerto);
            s2.setSoTimeout(5000);
            var out2 = new DataOutputStream(new BufferedOutputStream(s2.getOutputStream()));
            var in2 = new DataInputStream(new BufferedInputStream(s2.getInputStream()));
            out2.writeUTF("BANCO1");
            out2.writeUTF("nodo2");
            out2.writeUTF("127.0.0.1");
            out2.writeLong(10485760L);
            out2.flush();

            responderTrabajadorSimulado(in1, out1, raiz.resolve("trabajo-nodo1"));
            responderTrabajadorSimulado(in2, out2, raiz.resolve("trabajo-nodo2"));

            maestro.join(5000);
            exigir(!maestro.isAlive(), "El maestro no termino a tiempo");
            exigir(errorMaestro.get() == null, "Error inesperado en maestro: " + errorMaestro.get());
            exigir(Files.exists(salida.resolve("EXITO.txt")), "Trabajo legitimo no genero EXITO.txt");
        } finally {
            cerrar(sDesc); cerrar(s1); cerrar(sDup); cerrar(s2);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void probarCuotaCeroYRedistribucion() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-cuota-cero-");
        Socket s1 = null, s2 = null;
        Thread maestro = null;
        AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 4000, "nodo1,nodo2", Map.of(
                    "nodo1", new int[]{4, 10},
                    "nodo2", new int[]{1, 10}
            ));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "100", "10"});

            Path salidaSerial = raiz.resolve("serial");
            Coordinador.main(new String[]{"serial", datos.toString(), salidaSerial.toString()});

            Path salidaDist = raiz.resolve("distribuido");
            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salidaDist);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            s1 = new Socket("127.0.0.1", puerto);
            s1.setSoTimeout(5000);
            var out1 = new DataOutputStream(new BufferedOutputStream(s1.getOutputStream()));
            var in1 = new DataInputStream(new BufferedInputStream(s1.getInputStream()));
            out1.writeUTF("BANCO1"); out1.writeUTF("nodo1"); out1.writeUTF("127.0.0.1");
            out1.writeLong(10485760L); out1.flush();

            // 1024 bytes -> (1024/3 - 1024) <= 0 -> limite = 0 (cuota cero para nodo2)
            s2 = new Socket("127.0.0.1", puerto);
            s2.setSoTimeout(5000);
            var out2 = new DataOutputStream(new BufferedOutputStream(s2.getOutputStream()));
            var in2 = new DataInputStream(new BufferedInputStream(s2.getInputStream()));
            out2.writeUTF("BANCO1"); out2.writeUTF("nodo2"); out2.writeUTF("127.0.0.1");
            out2.writeLong(1024L); out2.flush();

            responderTrabajadorSimulado(in1, out1, raiz.resolve("trabajo-nodo1"));
            responderTrabajadorSimulado(in2, out2, raiz.resolve("trabajo-nodo2"));

            maestro.join(5000);
            exigir(!maestro.isAlive(), "Maestro no termino a tiempo");
            exigir(errorMaestro.get() == null, "Error en maestro con cuota cero: " + errorMaestro.get());

            String shaSerial = Transferencia.sha(salidaSerial.resolve("serial.csv"));
            String shaConsolidado = Transferencia.sha(salidaDist.resolve("consolidado.csv"));
            exigir(shaSerial.equals(shaConsolidado), "SHA-256 difiere en redistribucion por cuota cero");
            exigir(Files.exists(salidaDist.resolve("EXITO.txt")), "Debe existir EXITO.txt");

            Path dirJob = buscarDirectorioJob(salidaDist);
            List<String> lineasNodo1 = Files.readAllLines(dirJob.resolve("nodo1-entrada.csv"));
            List<String> lineasNodo2 = Files.readAllLines(dirJob.resolve("nodo2-entrada.csv"));
            exigir(lineasNodo1.size() == 101, "nodo1 debio recibir las 100 filas + cabecera");
            exigir(lineasNodo2.size() == 1, "nodo2 debio recibir cuota cero (solo cabecera)");
        } finally {
            cerrar(s1); cerrar(s2);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void probarAlmacenamientoInsuficienteEnCoordinador() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-insuficiente-");
        Socket s1 = null, s2 = null;
        Thread maestro = null;
        AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 4000, "nodo1,nodo2", Map.of(
                    "nodo1", new int[]{1, 10},
                    "nodo2", new int[]{1, 10}
            ));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "500", "20"});
            Path salida = raiz.resolve("salida");

            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salida);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            // Ambos nodos informan 1024 bytes -> limites=[0, 0] -> capacidad 0 < 500 registros
            s1 = new Socket("127.0.0.1", puerto);
            var out1 = new DataOutputStream(new BufferedOutputStream(s1.getOutputStream()));
            out1.writeUTF("BANCO1"); out1.writeUTF("nodo1"); out1.writeUTF("127.0.0.1");
            out1.writeLong(1024L); out1.flush();

            s2 = new Socket("127.0.0.1", puerto);
            var out2 = new DataOutputStream(new BufferedOutputStream(s2.getOutputStream()));
            out2.writeUTF("BANCO1"); out2.writeUTF("nodo2"); out2.writeUTF("127.0.0.1");
            out2.writeLong(1024L); out2.flush();

            maestro.join(5000);
            exigir(!maestro.isAlive(), "Maestro debio terminar por fallo de almacenamiento");
            exigir(errorMaestro.get() instanceof IOException, "Debio propagar IOException de almacenamiento insuficiente");
            exigir(!Files.exists(salida.resolve("EXITO.txt")), "No debe existir EXITO.txt tras fallo de almacenamiento");
            exigir(!Files.exists(salida.resolve("consolidado.csv")), "No debe existir consolidado.csv tras fallo de almacenamiento");
        } finally {
            cerrar(s1); cerrar(s2);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void probarDesconexionTrabajador() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-desconexion-");
        Socket s = null;
        Thread maestro = null;
        AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 5000, "nodo1", Map.of("nodo1", new int[]{1, 10}));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "100", "10"});
            Path salida = raiz.resolve("salida");

            maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salida);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            s = new Socket("127.0.0.1", puerto);
            var out = new DataOutputStream(s.getOutputStream());
            out.writeUTF("BANCO1");
            out.writeUTF("nodo1");
            out.writeUTF("127.0.0.1");
            out.writeLong(10485760L);
            out.flush();
            s.close(); // Desconexion abrupta

            maestro.join(5000);
            exigir(!maestro.isAlive(), "Maestro debio terminar");
            exigir(errorMaestro.get() != null, "Maestro debio registrar error por desconexion");
            exigir(!Files.exists(salida.resolve("EXITO.txt")), "No debe existir EXITO.txt tras desconexion");
        } finally {
            cerrar(s);
            if (maestro != null && maestro.isAlive()) { maestro.interrupt(); maestro.join(2000); }
            borrar(raiz);
        }
    }

    private static void responderTrabajadorSimulado(DataInputStream in, DataOutputStream out, Path dir) throws IOException {
        Files.createDirectories(dir);
        in.readUTF(); // job
        in.readLong(); // cuota
        Path datos = dir.resolve("particion.csv");
        Path resultado = dir.resolve("resultado.csv");
        Transferencia.recibir(in, datos, 10485760L);
        var r = Procesamiento.procesar(datos);
        r.guardar(resultado);
        out.writeLong(r.cantidad());
        out.writeLong(1000000L); // 1 ms
        Transferencia.enviar(out, resultado);
        String ok = in.readUTF();
        if (!"OK".equals(ok)) throw new IOException("Confirmacion incorrecta del maestro: " + ok);
    }

    private static int buscarPuertoLibre() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Path crearConfiguracion(Path dir, int puerto, int esperaMs, String nodos, Map<String, int[]> pesosLimites) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("maestro.ip=127.0.0.1\nmaestro.puerto=").append(puerto).append("\n");
        sb.append("espera.ms=").append(esperaMs).append("\nsocket.ms=5000\n");
        sb.append("nodos=").append(nodos).append("\n");
        for (var e : pesosLimites.entrySet()) {
            sb.append(e.getKey()).append(".ip=127.0.0.1\n");
            sb.append(e.getKey()).append(".capacidad=").append(e.getValue()[0]).append("\n");
            sb.append(e.getKey()).append(".almacenamiento.mb=").append(e.getValue()[1]).append("\n");
        }
        Path p = dir.resolve("cluster-" + puerto + ".properties");
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
        return p;
    }

    private static Process ejecutarTrabajador(Path config, String id, Path ipFile, Path dirTrabajo) throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        String cp = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder(
                java, "-cp", cp,
                "Trabajador", config.toAbsolutePath().toString(), id, ipFile.toAbsolutePath().toString(), dirTrabajo.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static void esperarProceso(Process p, String nombre) throws Exception {
        boolean ok = p.waitFor(15, TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            p.waitFor(3, TimeUnit.SECONDS);
            String out = leerSalidaDisponible(p);
            throw new AssertionError("Timeout en " + nombre + ":\n" + out);
        }
        if (p.exitValue() != 0) {
            String out = leerSalidaDisponible(p);
            throw new AssertionError("Fallo " + nombre + " (exit=" + p.exitValue() + "):\n" + out);
        }
    }

    private static String leerSalidaDisponible(Process p) {
        try {
            InputStream in = p.getInputStream();
            byte[] b = in.readNBytes(in.available());
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<salida no disponible: " + e + ">";
        }
    }

    private static void matarProceso(Process p) {
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            try { p.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        }
    }

    private static void cerrar(AutoCloseable c) {
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    private static Path buscarDirectorioJob(Path salida) throws IOException {
        try (var stream = Files.list(salida)) {
            return stream.filter(Files::isDirectory).findFirst().orElseThrow(() -> new IOException("No se encontro directorio de job"));
        }
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }

    private static void borrar(Path p) {
        try {
            if (Files.isDirectory(p)) {
                try (var stream = Files.list(p)) {
                    for (Path h : stream.toList()) borrar(h);
                }
            }
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }
}
