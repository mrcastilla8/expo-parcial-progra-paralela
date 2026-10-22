import java.io.RandomAccessFile;
import java.nio.file.*;

public class PruebaPatrones {
    public static void main(String[] args) throws Exception {
        Path p=Files.createTempFile("patrones-", ".dat");
        try {
            try(var f=new RandomAccessFile(p.toFile(),"rw")) {f.setLength(65534); f.seek(65534); f.writeBytes("ERRORERROR");}
            if(Operaciones.patrones(p)!=2) throw new AssertionError("Patron entre bloques perdido");
            System.out.println("Busqueda de patrones entre bloques: OK");
        } finally {Files.deleteIfExists(p);}
    }
}
