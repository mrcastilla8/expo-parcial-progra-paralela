import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

public final class Transferencia {
    static String sha(Path p) throws IOException {
        try {MessageDigest d=MessageDigest.getInstance("SHA-256"); byte[] b=new byte[65536]; int n;
            try(var f=Files.newInputStream(p)) {while((n=f.read(b))!=-1) d.update(b,0,n);} return HexFormat.of().formatHex(d.digest());
        } catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    static void enviar(DataOutputStream out,Path p) throws IOException {
        out.writeLong(Files.size(p)); out.writeUTF(sha(p)); try(var f=Files.newInputStream(p)) {f.transferTo(out);} out.flush();
    }
    static void recibir(DataInputStream in,Path p,long limite) throws IOException {
        long cantidad=in.readLong(); String hash=in.readUTF(); if(cantidad<0||cantidad>limite) throw new IOException("Transferencia excede almacenamiento");
        Path tmp=p.resolveSibling(p.getFileName()+".tmp");
        try {
            try(var f=Files.newOutputStream(tmp)) {byte[] b=new byte[65536]; long falta=cantidad; while(falta>0) {int n=in.read(b,0,(int)Math.min(b.length,falta)); if(n<0) throw new EOFException("Transferencia incompleta"); f.write(b,0,n); falta-=n;}}
            if(!sha(tmp).equals(hash)) throw new IOException("SHA-256 de transferencia incorrecto");
            Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
    }
}
