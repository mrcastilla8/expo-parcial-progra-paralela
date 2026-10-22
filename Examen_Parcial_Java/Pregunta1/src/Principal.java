import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class Principal {
    final Path directorio;
    final List<String> registro=Collections.synchronizedList(new ArrayList<>());
    Principal(String modo) throws IOException { directorio=Path.of("salida",modo); Files.createDirectories(directorio); Files.deleteIfExists(directorio.resolve("EXITO.txt")); }
    Path p(char nodo) {return directorio.resolve(nodo+".dat");}
    void nodo(char n) {
        long inicio=System.nanoTime(); anotar(n,"INICIO",inicio,0);
        Path temporal=directorio.resolve(n+".tmp");
        try {
            switch(n) {
                case 'A' -> Operaciones.unir(temporal,Path.of("entrada","registro1.txt"));
                case 'B' -> Operaciones.unir(temporal,Path.of("entrada","registro2.txt"));
                case 'C' -> Operaciones.unir(temporal,p('A'),p('B'));
                case 'D' -> Operaciones.unir(temporal,p('C'),p('C'),p('C'));
                case 'E' -> Operaciones.transformar(p('C'),temporal);
                case 'F' -> Operaciones.extraer(p('D'),temporal);
                case 'G' -> Operaciones.extraer(p('E'),temporal);
                case 'H' -> {Operaciones.unir(temporal,p('F'),p('G')); System.out.println("Patrones ERROR en H: "+Operaciones.patrones(temporal));}
                default -> throw new IllegalArgumentException("Nodo desconocido");
            }
            Files.move(temporal,p(n),StandardCopyOption.REPLACE_EXISTING);
            anotar(n,"FIN",System.nanoTime(),System.nanoTime()-inicio);
        } catch(IOException e) { anotar(n,"ERROR",System.nanoTime(),System.nanoTime()-inicio); throw new CompletionException(e); }
    }
    synchronized void anotar(char n,String evento,long reloj,long duracion) {
        String s=n+","+evento+","+Thread.currentThread().getName()+","+reloj+","+duracion+","+Instant.now();
        registro.add(s); System.out.println(s);
    }
    long ejecutar(boolean paralelo) throws Exception {
        long t=System.nanoTime(); ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            if(!paralelo) for(char c='A';c<='H';c++) nodo(c);
            else {
                CompletableFuture<Void> a=CompletableFuture.runAsync(()->nodo('A'),pool),b=CompletableFuture.runAsync(()->nodo('B'),pool);
                var c=CompletableFuture.allOf(a,b).thenRunAsync(()->nodo('C'),pool);
                var d=c.thenRunAsync(()->nodo('D'),pool); var e=c.thenRunAsync(()->nodo('E'),pool);
                var f=d.thenRunAsync(()->nodo('F'),pool); var g=e.thenRunAsync(()->nodo('G'),pool);
                CompletableFuture.allOf(f,g).thenRunAsync(()->nodo('H'),pool).join();
            }
            long total=System.nanoTime()-t;
            Files.writeString(directorio.resolve("EXITO.txt"),Operaciones.sha(p('H'))+"\nTiempo ns="+total+"\n");
            return total;
        } finally {pool.shutdown(); if(!pool.awaitTermination(60,TimeUnit.SECONDS)) pool.shutdownNow(); Files.write(directorio.resolve("registro.csv"),registro);}
    }
    static void generar(long bytes) throws IOException {
        if(bytes<1) throw new IllegalArgumentException("Tamano positivo requerido"); Files.createDirectories(Path.of("entrada"));
        for(int i=1;i<=2;i++) try(RandomAccessFile f=new RandomAccessFile("entrada/registro"+i+".txt","rw")) {
            f.setLength(0); byte[] b=("2026-10-15 servidor="+i+" ERROR respaldo pendiente; INFO copia de registros\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            for(long n=0;n<bytes;) {int k=(int)Math.min(b.length,bytes-n); f.write(b,0,k); n+=k;}
        }
    }
    public static void main(String[] args) {
        try {
            if(args.length==0) System.out.println("Elegir modo: serial | paralelo | comparar");
            String modo=args.length>0?args[0]:new Scanner(System.in).nextLine().trim();
            if(modo.equals("generar")) {generar(args.length>1?Long.parseLong(args[1]):1048576); return;}
            if(!Set.of("serial","paralelo","comparar").contains(modo)) throw new IllegalArgumentException("Uso: generar [bytes por archivo] | serial | paralelo | comparar");
            if(modo.equals("comparar")) {
                long s=new Principal("serial").ejecutar(false),p=new Principal("paralelo").ejecutar(true);
                for(char c='A';c<='H';c++) if(!Operaciones.sha(Path.of("salida/serial/"+c+".dat")).equals(Operaciones.sha(Path.of("salida/paralelo/"+c+".dat")))) throw new IOException("Diferencia nodo "+c);
                System.out.printf(Locale.ROOT,"Tiempo serial: %.3f ms%nTiempo paralelo: %.3f ms%nSpeedup: %.3f%nValidacion SHA-256 A-H: OK%n",s/1e6,p/1e6,p>0?(double)s/p:0);
            } else System.out.printf("Tiempo total: %.3f ms%n",new Principal(modo).ejecutar(modo.equals("paralelo"))/1e6);
        } catch(Exception e) {System.err.println("ERROR: "+e); System.exit(1);}
    }
}
