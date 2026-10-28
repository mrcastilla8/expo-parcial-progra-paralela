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
        probarAlmacenamientoInsuficiente();
        probarDesconexionTrabajador();
        System.out.println("Regresion del cluster y balanceo tras migrar a Thread: OK");
    }

    private static void probarClusterNormal3Jvm() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-normal-");
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
            Thread maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salidaDist);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();

            Thread.sleep(300);

            Process p1 = ejecutarTrabajador(configPath, "nodo1", ipFile, raiz.resolve("trabajo1"));
            Process p2 = ejecutarTrabajador(configPath, "nodo2", ipFile, raiz.resolve("trabajo2"));
            Process p3 = ejecutarTrabajador(configPath, "nodo3", ipFile, raiz.resolve("trabajo3"));

            esperarProceso(p1, "trabajador 1");
            esperarProceso(p2, "trabajador 2");
            esperarProceso(p3, "trabajador 3");

            maestro.join(5000);
            exigir(!maestro.isAlive() && errorMaestro.get() == null, "Fallo en hilo maestro: " + errorMaestro.get());

            // 1. Verificacion SHA-256 consolidado vs serial
            String shaSerial = Transferencia.sha(salidaSerial.resolve("serial.csv"));
            String shaConsolidado = Transferencia.sha(salidaDist.resolve("consolidado.csv"));
            exigir(shaSerial.equals(shaConsolidado), "SHA-256 consolidado difiere del serial");
            exigir(Files.exists(salidaDist.resolve("EXITO.txt")), "Falta EXITO.txt");

            // 2. Unicidad de particiones, ausencia de duplicados/perdidas y reparto 4:2:1
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

            // Proporcion 4:2:1 exacta para 700 filas = 400, 200, 100
            exigir(filasPorNodo.get("nodo1") == 400, "nodo1 debe tener 400 filas");
            exigir(filasPorNodo.get("nodo2") == 200, "nodo2 debe tener 200 filas");
            exigir(filasPorNodo.get("nodo3") == 100, "nodo3 debe tener 100 filas");

            // Verificacion estricta de continuidad: IDs 1..700 sin faltantes ni duplicados
            exigir(todosLosIds.size() == 700, "Totalidad de filas incorrecta");
            for (int i = 0; i < 700; i++) {
                exigir(todosLosIds.get(i) == (long) (i + 1), "ID fuera de orden o duplicado: " + todosLosIds.get(i));
            }
        } finally {
            borrar(raiz);
        }
    }

    private static void probarDisponibilidadParcial() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-parcial-");
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
            Thread maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salidaDist);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            // Solo conectan nodo1 y nodo2 (nodo3 ausente)
            Process p1 = ejecutarTrabajador(configPath, "nodo1", ipFile, raiz.resolve("trabajo1"));
            Process p2 = ejecutarTrabajador(configPath, "nodo2", ipFile, raiz.resolve("trabajo2"));

            esperarProceso(p1, "trabajador 1 parcial");
            esperarProceso(p2, "trabajador 2 parcial");

            maestro.join(10000);
            exigir(!maestro.isAlive() && errorMaestro.get() == null, "Maestro no tolero disponibilidad parcial: " + errorMaestro.get());

            String shaSerial = Transferencia.sha(salidaSerial.resolve("serial.csv"));
            String shaConsolidado = Transferencia.sha(salidaDist.resolve("consolidado.csv"));
            exigir(shaSerial.equals(shaConsolidado), "SHA-256 no coincide en disponibilidad parcial");
        } finally {
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
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 5000, "nodo1", Map.of("nodo1", new int[]{1, 10}));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "50", "10"});
            Path salida = raiz.resolve("salida");

            Thread maestro = new Thread(() -> {
                try { Coordinador.distribuir(config, datos, salida); } catch (Exception ignored) {}
            });
            maestro.start();
            Thread.sleep(200);

            // 1. Nodo desconocido
            try (Socket sDesc = new Socket("127.0.0.1", puerto)) {
                var out = new DataOutputStream(sDesc.getOutputStream());
                out.writeUTF("BANCO1");
                out.writeUTF("nodoFantasma");
                out.writeUTF("127.0.0.1");
                out.writeLong(10485760L);
                out.flush();
                var in = new DataInputStream(sDesc.getInputStream());
                try {
                    in.readUTF();
                } catch (IOException esperado) {} // Maestro cierra conexion
            }

            // 2. Nodo duplicado
            try (Socket s1 = new Socket("127.0.0.1", puerto)) {
                var out1 = new DataOutputStream(s1.getOutputStream());
                out1.writeUTF("BANCO1");
                out1.writeUTF("nodo1");
                out1.writeUTF("127.0.0.1");
                out1.writeLong(10485760L);
                out1.flush();

                try (Socket s2 = new Socket("127.0.0.1", puerto)) {
                    var out2 = new DataOutputStream(s2.getOutputStream());
                    out2.writeUTF("BANCO1");
                    out2.writeUTF("nodo1"); // Mismo ID
                    out2.writeUTF("127.0.0.1");
                    out2.writeLong(10485760L);
                    out2.flush();
                    var in2 = new DataInputStream(s2.getInputStream());
                    try {
                        in2.readUTF();
                    } catch (IOException esperado) {} // Rechazado por duplicado
                }
            }

            maestro.join(3000);
        } finally {
            borrar(raiz);
        }
    }

    private static void probarAlmacenamientoInsuficiente() throws Exception {
        try {
            Balanceador.repartir(1000, new int[]{1, 1}, new long[]{200, 200});
            throw new AssertionError("Debio fallar por almacenamiento insuficiente");
        } catch (IOException esperado) {}
    }

    private static void probarDesconexionTrabajador() throws Exception {
        Path raiz = Files.createTempDirectory("cluster-desconexion-");
        try {
            int puerto = buscarPuertoLibre();
            Path configPath = crearConfiguracion(raiz, puerto, 5000, "nodo1", Map.of("nodo1", new int[]{1, 10}));
            Configuracion config = new Configuracion(configPath);
            Path datos = raiz.resolve("transacciones.csv");
            Generador.main(new String[]{datos.toString(), "100", "10"});
            Path salida = raiz.resolve("salida");

            AtomicReference<Throwable> errorMaestro = new AtomicReference<>();
            Thread maestro = new Thread(() -> {
                try {
                    Coordinador.distribuir(config, datos, salida);
                } catch (Throwable t) {
                    errorMaestro.set(t);
                }
            });
            maestro.start();
            Thread.sleep(200);

            // Conectar y cerrar inmediatamente para forzar SocketException durante envio
            try (Socket s = new Socket("127.0.0.1", puerto)) {
                var out = new DataOutputStream(s.getOutputStream());
                out.writeUTF("BANCO1");
                out.writeUTF("nodo1");
                out.writeUTF("127.0.0.1");
                out.writeLong(10485760L);
                out.flush();
            }

            maestro.join(5000);
            exigir(!maestro.isAlive(), "Maestro debio terminar");
            exigir(errorMaestro.get() != null, "Maestro debio registrar error por desconexion");
            exigir(!Files.exists(salida.resolve("EXITO.txt")), "No debe existir EXITO.txt tras desconexion");
        } finally {
            borrar(raiz);
        }
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
        if (!ok || p.exitValue() != 0) {
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new AssertionError("Fallo " + nombre + " (exit=" + (ok ? p.exitValue() : "timeout") + "):\n" + out);
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
