import java.io.IOException;

public final class Balanceador {
    static long[] repartir(long registros,int[] pesos,long[] limites) throws IOException {
        long[] cuota=new long[pesos.length]; long pendiente=registros;
        while(pendiente>0) {
            long peso=0; for(int i=0;i<pesos.length;i++) if(cuota[i]<limites[i]) peso+=pesos[i];
            if(peso==0) throw new IOException("Almacenamiento insuficiente para todos los registros");
            long base=pendiente,asignado=0;
            for(int i=0;i<pesos.length;i++) if(cuota[i]<limites[i]) {
                long propuesta=(long)Math.floor((double)base*pesos[i]/peso);
                long n=Math.min(limites[i]-cuota[i],propuesta); cuota[i]+=n; asignado+=n;
            }
            pendiente-=asignado;
            if(asignado==0) for(int i=0;i<pesos.length&&pendiente>0;i++) if(cuota[i]<limites[i]) {cuota[i]++; pendiente--;}
        }
        return cuota;
    }
}
