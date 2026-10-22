import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

public final class Procesamiento {
    static final String CABECERA="id_transaccion,id_cuenta,fecha,tipo,monto,sucursal";
    record Cuenta(long cantidad,long depositos,long retiros) {
        Cuenta sumar(Cuenta c) {return new Cuenta(Math.addExact(cantidad,c.cantidad),Math.addExact(depositos,c.depositos),Math.addExact(retiros,c.retiros));}
    }
    static final class Resumen {
        final TreeMap<String,Cuenta> cuentas=new TreeMap<>();
        void agregar(String id,Cuenta c) {cuentas.merge(id,c,Cuenta::sumar);}
        void unir(Resumen r) {r.cuentas.forEach(this::agregar);}
        long cantidad() {long n=0; for(Cuenta c:cuentas.values()) n=Math.addExact(n,c.cantidad); return n;}
        void guardar(Path ruta) throws IOException {
            Files.createDirectories(ruta.toAbsolutePath().getParent());
            Path tmp=ruta.resolveSibling(ruta.getFileName()+".tmp");
            try(var w=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)) {
                w.write("cuenta,cantidad,depositos_centimos,retiros_centimos,variacion_neta_centimos\n");
                Cuenta total=new Cuenta(0,0,0);
                for(var e:cuentas.entrySet()) {Cuenta c=e.getValue(); total=total.sumar(c); escribir(w,e.getKey(),c);}
                escribir(w,"TOTAL",total);
            }
            Files.move(tmp,ruta,StandardCopyOption.REPLACE_EXISTING);
        }
        static void escribir(Writer w,String id,Cuenta c) throws IOException {w.write(id+","+c.cantidad+","+c.depositos+","+c.retiros+","+Math.subtractExact(c.depositos,c.retiros)+"\n");}
    }
    static Resumen procesar(Path archivo) throws IOException {
        Resumen r=new Resumen();
        try(var b=Files.newBufferedReader(archivo,StandardCharsets.UTF_8)) {
            if(!CABECERA.equals(b.readLine())) throw new IOException("Cabecera CSV invalida");
            String s; long previo=0;
            while((s=b.readLine())!=null) {
                String[] c=s.split(",",-1);
                if(c.length!=6) throw new IOException("Registro invalido: "+s);
                long id=Long.parseLong(c[0]); if(id<=previo) throw new IOException("IDs deben ser positivos, unicos y crecientes"); previo=id;
                if(!c[1].matches("CTA[0-9]{5,9}")) throw new IOException("Cuenta invalida");
                LocalDate.parse(c[2]); if(!c[5].matches("[A-Z]+")) throw new IOException("Sucursal invalida");
                long monto=new BigDecimal(c[4]).movePointRight(2).longValueExact(); if(monto<=0) throw new IOException("Monto no positivo");
                if(!c[3].equals("DEPOSITO")&&!c[3].equals("RETIRO")) throw new IOException("Tipo invalido");
                r.agregar(c[1],new Cuenta(1,c[3].equals("DEPOSITO")?monto:0,c[3].equals("RETIRO")?monto:0));
            }
        }
        return r;
    }
    static Resumen leerResumen(Path p) throws IOException {
        Resumen r=new Resumen();
        try(var b=Files.newBufferedReader(p)) {b.readLine(); String s; while((s=b.readLine())!=null) {String[] c=s.split(","); if(c.length!=5) throw new IOException("Resumen invalido"); if(c[0].equals("TOTAL")) continue; Cuenta v=new Cuenta(Long.parseLong(c[1]),Long.parseLong(c[2]),Long.parseLong(c[3])); if(v.cantidad<0||v.depositos<0||v.retiros<0||Long.parseLong(c[4])!=Math.subtractExact(v.depositos,v.retiros)||r.cuentas.putIfAbsent(c[0],v)!=null) throw new IOException("Parcial invalido"); }} return r;
    }
}
