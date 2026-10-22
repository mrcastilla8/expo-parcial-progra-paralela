import java.util.Arrays;
import java.io.IOException;

public class PruebaBalanceador {
    public static void main(String[] args) throws Exception {
        long[] a=Balanceador.repartir(700,new int[]{4,2,1},new long[]{1000,1000,1000});
        if(!Arrays.equals(a,new long[]{400,200,100})) throw new AssertionError(Arrays.toString(a));
        long[] b=Balanceador.repartir(700,new int[]{4,2,1},new long[]{100,1000,1000});
        if(b[0]!=100 || Arrays.stream(b).sum()!=700 || b[1]<=b[2]) throw new AssertionError(Arrays.toString(b));
        try {Balanceador.repartir(700,new int[]{4,2,1},new long[]{100,100,100}); throw new AssertionError("Debe rechazar espacio insuficiente");} catch(IOException esperado) {}
        System.out.println("Balanceo 4:2:1="+Arrays.toString(a)+"; con limite="+Arrays.toString(b)+"; insuficiencia rechazada: OK");
    }
}
