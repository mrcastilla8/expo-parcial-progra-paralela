import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Trabajador {
    public static void main(String[] args) {
        try {
            if(args.length!=4) throw new IllegalArgumentException("Trabajador cluster.properties id archivo-ip carpeta-trabajo");
            Configuracion c=new Configuracion(Path.of(args[0])); String id=args[1],ip=Configuracion.leerIp(Path.of(args[2])); Configuracion.local(ip);
            var nodo=c.nodos.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Nodo no configurado"));
            if(!nodo.ip().equals(ip)) throw new IOException("IP local no coincide con cluster");
            Path dir=Path.of(args[3]); Files.createDirectories(dir); Files.deleteIfExists(dir.resolve("EXITO.txt"));
            long disponible=Math.min(nodo.bytes(),Files.getFileStore(dir).getUsableSpace());
            try(Socket s=new Socket()) {
                s.bind(new InetSocketAddress(Configuracion.ip(ip),0)); s.connect(new InetSocketAddress(c.texto("maestro.ip"),c.puerto()),c.numero("espera.ms",100,600000)); s.setSoTimeout(c.numero("socket.ms",100,3600000));
                var in=new DataInputStream(new BufferedInputStream(s.getInputStream())); var out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                out.writeUTF("BANCO1"); out.writeUTF(id); out.writeUTF(ip); out.writeLong(disponible); out.flush();
                String job=in.readUTF(); if(!job.matches("[a-zA-Z0-9-]+")) throw new IOException("Trabajo invalido"); long esperado=in.readLong();
                Path trabajo=dir.resolve(job); Files.createDirectory(trabajo);
                Path datos=trabajo.resolve("particion.csv"),resultado=trabajo.resolve("parcial.csv");
                Transferencia.recibir(in,datos,Math.max(0,disponible/3));
                System.out.println("Nodo "+id+" IP="+ip+" recibidos bytes="+Files.size(datos)+" registros="+esperado);
                long inicio=System.nanoTime(); var r=Procesamiento.procesar(datos);
                if(r.cantidad()!=esperado) throw new IOException("Cantidad recibida incorrecta");
                r.guardar(resultado); if(Files.size(datos)+Files.size(resultado)>disponible) throw new IOException("Limite de almacenamiento excedido");
                out.writeLong(r.cantidad()); out.writeLong(System.nanoTime()-inicio); Transferencia.enviar(out,resultado);
                if(!in.readUTF().equals("OK")) throw new IOException("Maestro no confirmo resultado");
                Files.writeString(dir.resolve("EXITO.txt"),job+"\nRegistros="+r.cantidad()+"\n");
                System.out.println("Resultado confirmado: "+r.cantidad());
            }
        } catch(Exception e) {System.err.println("ERROR trabajador: "+e); System.exit(1);}
    }
}
