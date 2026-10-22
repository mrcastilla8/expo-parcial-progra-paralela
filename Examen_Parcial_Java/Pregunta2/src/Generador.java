import java.nio.file.*;
import java.util.*;
import java.math.BigDecimal;

public class Generador {
    public static void main(String[] args) throws Exception {
        if(args.length<2) throw new IllegalArgumentException("Generador archivo registros [cuentas]");
        long n=Long.parseLong(args[1]); int cuentas=args.length>2?Integer.parseInt(args[2]):1000;
        if(n<1||cuentas<1||cuentas>100000000) throw new IllegalArgumentException("Cantidad fuera de rango");
        Path p=Path.of(args[0]); Files.createDirectories(p.toAbsolutePath().getParent()); Random r=new Random(20261015);
        try(var w=Files.newBufferedWriter(p)) {w.write(Procesamiento.CABECERA+"\n"); for(long i=1;i<=n;i++) {
            w.write(i+",CTA"+String.format(Locale.ROOT,"%05d",1+r.nextInt(cuentas))+",2026-10-"+String.format(Locale.ROOT,"%02d",1+r.nextInt(28))+","+(r.nextBoolean()?"DEPOSITO":"RETIRO")+","+BigDecimal.valueOf(1+r.nextInt(500000),2).toPlainString()+","+(r.nextBoolean()?"LIMA":"AREQUIPA")+"\n");
        }} System.out.println("Generados "+n+" registros en "+p);
    }
}
