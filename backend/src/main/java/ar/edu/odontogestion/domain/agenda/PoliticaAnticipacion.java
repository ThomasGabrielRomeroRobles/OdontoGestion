package ar.edu.odontogestion.domain.agenda;

import java.time.Duration;
import java.time.Instant;

/**
 * Strategy de anticipación mínima de reserva (design D7, RN-AG-11): para el odontólogo cuenta desde ahora; para
 * solicitudes públicas, desde ahora más la anticipación configurada.
 */
public final class PoliticaAnticipacion {

    private final int anticipacionMin;

    private PoliticaAnticipacion(int anticipacionMin) {
        this.anticipacionMin = anticipacionMin;
    }

    /** Sin anticipación: huecos desde el momento actual. */
    public static PoliticaAnticipacion odontologo() {
        return new PoliticaAnticipacion(0);
    }

    /** Solo huecos que empiezan al menos {@code anticipacionMin} minutos después de ahora. */
    public static PoliticaAnticipacion publica(int anticipacionMin) {
        return new PoliticaAnticipacion(anticipacionMin);
    }

    /** Primer instante de inicio admitido en el momento {@code ahora}. */
    public Instant minimoInicio(Instant ahora) {
        return ahora.plus(Duration.ofMinutes(anticipacionMin));
    }
}
