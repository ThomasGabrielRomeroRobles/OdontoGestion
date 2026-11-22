package ar.edu.odontogestion.domain.agenda;

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
        throw new UnsupportedOperationException("Pendiente: tarea 2.4");
    }

    /** Solo huecos que empiezan al menos {@code anticipacionMin} minutos después de ahora. */
    public static PoliticaAnticipacion publica(int anticipacionMin) {
        throw new UnsupportedOperationException("Pendiente: tarea 2.4");
    }

    /** Primer instante de inicio admitido en el momento {@code ahora}. */
    public Instant minimoInicio(Instant ahora) {
        throw new UnsupportedOperationException("Pendiente: tarea 2.4");
    }

    int anticipacionMin() {
        return anticipacionMin;
    }
}
