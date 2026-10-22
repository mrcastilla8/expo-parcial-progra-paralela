import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

public final class Operaciones {
    static final int BLOQUE = 65536;
    static void segmento(Path origen, RandomAccessFile destino, long inicio, long cantidad) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(origen.toFile(), "r")) {
            f.seek(inicio);
            byte[] b = new byte[BLOQUE];
            while (cantidad > 0) {
                int n = f.read(b, 0, (int)Math.min(b.length, cantidad));
                if (n < 0) throw new EOFException("Segmento incompleto: " + origen);
                destino.write(b, 0, n);
                cantidad -= n;
            }
        }
    }
    static void unir(Path salida, Path... entradas) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(salida.toFile(), "rw")) {
            f.setLength(0);
            for (Path p : entradas) segmento(p, f, 0, Files.size(p));
        }
    }
    static void extraer(Path origen, Path salida) throws IOException {
        long inicio = Files.size(origen) / 4, cantidad = Files.size(origen) / 2;
        try (RandomAccessFile f = new RandomAccessFile(salida.toFile(), "rw")) {
            f.setLength(0); segmento(origen, f, inicio, cantidad);
        }
    }
    static void transformar(Path origen, Path salida) throws IOException {
        unir(salida, origen);
        try (RandomAccessFile f = new RandomAccessFile(salida.toFile(), "rw")) {
            byte[] b = new byte[BLOQUE];
            long posicion = 0; int n;
            while ((n = f.read(b)) != -1) {
                for (int i = 0; i < n; i++) if (b[i] >= 'a' && b[i] <= 'z') b[i] -= 32;
                f.seek(posicion); f.write(b, 0, n); posicion += n; f.seek(posicion);
            }
        }
    }
    static long patrones(Path p) throws IOException {
        byte[] patron = "ERROR".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int[] prefijo = {0,0,0,0,0};
        for (int i=1,j=0;i<patron.length;i++) { while(j>0 && patron[i]!=patron[j]) j=prefijo[j-1]; if(patron[i]==patron[j]) j++; prefijo[i]=j; }
        long total=0; int j=0,n; byte[] b=new byte[BLOQUE];
        try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"r")) {
            while((n=f.read(b))!=-1) for(int i=0;i<n;i++) { while(j>0 && b[i]!=patron[j]) j=prefijo[j-1]; if(b[i]==patron[j]) j++; if(j==patron.length) {total++; j=prefijo[j-1];} }
        }
        return total;
    }
    static String sha(Path p) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256"); byte[] b=new byte[BLOQUE]; int n;
        try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"r")) {while((n=f.read(b))!=-1) d.update(b,0,n);}
        return HexFormat.of().formatHex(d.digest());
    }
}
