import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public final class Configuracion {
    final Properties p=new Properties();
    record Nodo(String id,String ip,int capacidad,long bytes) {}
    final List<Nodo> nodos=new ArrayList<>();
    Configuracion(Path archivo) throws IOException {
        try(var r=Files.newBufferedReader(archivo)) {p.load(r);}
        ip(texto("maestro.ip")); numero("maestro.puerto",1,65535); numero("espera.ms",100,600000); numero("socket.ms",100,3600000);
        Set<String> ids=new HashSet<>();
        for(String raw:texto("nodos").split(",")) {
            String id=raw.trim(); if(!id.matches("[a-zA-Z0-9_-]+") || !ids.add(id)) throw new IllegalArgumentException("Identificador invalido/duplicado");
            String direccion=texto(id+".ip"); ip(direccion);
            nodos.add(new Nodo(id,direccion,numero(id+".capacidad",1,1000000),Math.multiplyExact((long)numero(id+".almacenamiento.mb",1,100000000),1048576L)));
        }
        if(nodos.isEmpty()) throw new IllegalArgumentException("Sin nodos configurados");
    }
    String texto(String k) {String v=p.getProperty(k); if(v==null || v.isBlank()) throw new IllegalArgumentException("Falta "+k); return v.trim();}
    int numero(String k,int min,int max) {int v=Integer.parseInt(texto(k)); if(v<min||v>max) throw new IllegalArgumentException("Fuera de rango: "+k); return v;}
    int puerto() {return numero("maestro.puerto",1,65535);}
    static InetAddress ip(String s) throws UnknownHostException {
        if(!s.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IllegalArgumentException("IPv4 numerica requerida: "+s);
        for(String v:s.split("\\.")) if(Integer.parseInt(v)>255) throw new IllegalArgumentException("IP invalida: "+s);
        return InetAddress.getByName(s);
    }
    static String leerIp(Path ruta) throws IOException {String s=Files.readString(ruta).trim(); ip(s); return s;}
    static void local(String s) throws IOException {InetAddress a=ip(s); if(!a.isLoopbackAddress() && NetworkInterface.getByInetAddress(a)==null) throw new IOException("IP no pertenece a esta computadora: "+s);}
}
