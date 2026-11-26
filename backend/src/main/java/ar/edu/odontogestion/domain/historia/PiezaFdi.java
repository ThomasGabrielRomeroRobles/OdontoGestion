package ar.edu.odontogestion.domain.historia;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Pieza dental permanente en numeración FDI (RN-HC-03): cuadrante 1-4 y pieza 1-8 (11-18, 21-28, 31-38, 41-48).
 * Las temporarias (51-85) quedan fuera del MVP (C-05 design D3). La base repite la regla con un CHECK.
 */
public record PiezaFdi(int numero) {

    /** Las 32 piezas permanentes en orden de cuadrante (11..18, 21..28, 31..38, 41..48). */
    public static final List<PiezaFdi> PERMANENTES = IntStream.rangeClosed(1, 4)
            .boxed()
            .flatMap(c -> IntStream.rangeClosed(1, 8).mapToObj(p -> new PiezaFdi(c * 10 + p)))
            .toList();

    public PiezaFdi {
        if (!esPermanente(numero)) {
            throw new PiezaFdiInvalidaException();
        }
    }

    public static PiezaFdi of(int numero) {
        return new PiezaFdi(numero);
    }

    public static boolean esPermanente(int numero) {
        int cuadrante = numero / 10;
        int pieza = numero % 10;
        return numero > 0 && cuadrante >= 1 && cuadrante <= 4 && pieza >= 1 && pieza <= 8;
    }
}
