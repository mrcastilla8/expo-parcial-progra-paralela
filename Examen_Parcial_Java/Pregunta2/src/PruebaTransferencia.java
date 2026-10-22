import java.io.*;
import java.nio.file.*;

public class PruebaTransferencia {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("transferencia-"); Path destino=dir.resolve("resultado.csv");
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)) {out.writeLong(10); out.writeUTF("hash"); out.write(new byte[]{1,2,3});}
            try {Transferencia.recibir(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),destino,100); throw new AssertionError("Truncamiento aceptado");} catch(EOFException esperado) {}
            if(Files.exists(destino)||Files.exists(dir.resolve("resultado.csv.tmp"))) throw new AssertionError("Se publico archivo incompleto");
            bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)) {out.writeLong(3); out.writeUTF("hash-incorrecto"); out.write(new byte[]{1,2,3});}
            try {Transferencia.recibir(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),destino,100); throw new AssertionError("Hash incorrecto aceptado");} catch(IOException esperado) {}
            if(Files.exists(destino)) throw new AssertionError("Se publico archivo corrupto");
            System.out.println("Transferencias truncadas/corruptas rechazadas sin publicar resultados: OK");
        } finally {Files.deleteIfExists(dir.resolve("resultado.csv.tmp")); Files.deleteIfExists(destino); Files.deleteIfExists(dir);}
    }
}
