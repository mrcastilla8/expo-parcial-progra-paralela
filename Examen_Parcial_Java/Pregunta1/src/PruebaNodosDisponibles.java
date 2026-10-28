import java.nio.file.*;
import java.util.*;

public class PruebaNodosDisponibles {
    static void exigir(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        Path raiz = Files.createTempDirectory("nodos-disponibles-");
        try {
            Path a = raiz.resolve("a.csv"), b = raiz.resolve("b.csv"), c = raiz.resolve("c.csv");
            String cab = DatasetMultivariable.CABECERA;
            Files.writeString(a, cab + "\r\n1,1e0,+3.0,5,1\r\n");
            Files.writeString(b, cab + "\n2,2,2,5,3\n3,3,1,5,5\n");
            Path publicado = raiz.resolve("A.csv");
            Operaciones.particion(a, publicado);
            exigir(Files.readString(publicado).equals(cab + "\n1,1e0,+3.0,5,1\n"));
            Operaciones.unir(publicado, b, c);
            exigir(Files.readString(c).equals(cab + "\n1,1e0,+3.0,5,1\n2,2,2,5,3\n3,3,1,5,5\n"));
            Files.writeString(b, cab + "\n4,2,2,5,3\n");
            Path invalido = raiz.resolve("invalido.csv");
            try { Operaciones.unir(a, b, invalido); throw new AssertionError(); }
            catch (java.io.IOException esperado) { exigir(!Files.exists(invalido)); }
            Path f = raiz.resolve("F.csv"), g = raiz.resolve("G.csv"), h = raiz.resolve("H.txt");
            String[] vars = DatasetMultivariable.VARIABLES;
            StringBuilder fc = new StringBuilder("variable_1,variable_2,pearson\n");
            StringBuilder gc = new StringBuilder("variable,pendiente,tendencia\n");
            StringBuilder hc = new StringBuilder("CORRELACIONES\n");
            for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) {
                fc.append(vars[i]+","+vars[j]+",NO_DEFINIDA\n");
                hc.append(vars[i]+"|"+vars[j]+"=NO_DEFINIDA\n");
            }
            hc.append("TENDENCIAS\n");
            for (String v : vars) {
                gc.append(v+",0.000000,ESTABLE\n");
                hc.append(v+"|0.000000|ESTABLE\n");
            }
            Files.writeString(f, fc); Files.writeString(g, gc);
            Operaciones.resumen(f, g, h);
            exigir(Files.readString(h).equals(hc.toString()));
            Files.writeString(f, fc.toString().replaceFirst("NO_DEFINIDA", "1.000001"));
            try { Operaciones.resumen(f, g, raiz.resolve("rechazado.txt")); throw new AssertionError(); }
            catch (java.io.IOException esperado) { exigir(!Files.exists(raiz.resolve("rechazado.txt"))); }
            System.out.println("A/B/C: validacion, LF, tokens, orden y continuidad; H: formato y rechazo de Pearson invalido: OK");
        } finally {
            try (var rutas = Files.walk(raiz)) {
                for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) Files.delete(ruta);
            }
        }
    }
}
