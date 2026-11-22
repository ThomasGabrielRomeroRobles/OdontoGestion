package ar.edu.odontogestion.application.notificacion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Qué hacer tras un envío fallido (RN-NO-06, design D10): reprogramar con espera creciente o, al llegar al tope de
 * intentos, dejar la notificación {@code FALLIDA}. Si hay menos esperas que intentos se repite la última.
 */
public final class PoliticaReintento {

    private final int maxIntentos;
    private final List<Duration> esperas;

    public PoliticaReintento(int maxIntentos, List<Duration> esperas) {
        if (maxIntentos < 1) {
            throw new IllegalArgumentException("maxIntentos debe ser al menos 1");
        }
        if (esperas == null || esperas.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una espera");
        }
        this.maxIntentos = maxIntentos;
        this.esperas = List.copyOf(esperas);
    }

    /**
     * @param intentosRealizados intentos contando el que acaba de fallar (1 = primer intento)
     */
    public Decision trasFallo(int intentosRealizados, Instant ahora) {
        if (intentosRealizados < 1) {
            throw new IllegalArgumentException("intentosRealizados debe ser al menos 1");
        }
        Objects.requireNonNull(ahora, "ahora");
        if (intentosRealizados >= maxIntentos) {
            return new Decision.Fallida();
        }
        Duration espera = esperas.get(Math.min(intentosRealizados - 1, esperas.size() - 1));
        return new Decision.Reprogramar(ahora.plus(espera));
    }

    public int maxIntentos() {
        return maxIntentos;
    }

    public sealed interface Decision {

        record Reprogramar(Instant programadaPara) implements Decision {
        }

        record Fallida() implements Decision {
        }
    }
}
