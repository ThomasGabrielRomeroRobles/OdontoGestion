package ar.edu.odontogestion.domain.turno;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del turno y su tabla de transiciones (RN-TU-02, design D4). Los finales ({@code CANCELADO},
 * {@code EXPIRADO}, {@code AUSENTE}, {@code ATENDIDO}) no tienen salidas.
 */
public enum EstadoTurno {
    PENDIENTE_CONFIRMACION,
    RESERVADO,
    CONFIRMADO,
    CANCELADO,
    AUSENTE,
    ATENDIDO,
    EXPIRADO;

    private Set<EstadoTurno> destinos() {
        return switch (this) {
            case PENDIENTE_CONFIRMACION -> EnumSet.of(RESERVADO, EXPIRADO, CANCELADO);
            case RESERVADO -> EnumSet.of(CONFIRMADO, CANCELADO, AUSENTE, ATENDIDO);
            case CONFIRMADO -> EnumSet.of(CANCELADO, AUSENTE, ATENDIDO);
            case CANCELADO, AUSENTE, ATENDIDO, EXPIRADO -> EnumSet.noneOf(EstadoTurno.class);
        };
    }

    public boolean puedePasarA(EstadoTurno destino) {
        return destinos().contains(destino);
    }

    /**
     * Espejo en Java del predicado de {@code ex_turno_sin_superposicion} (V9, D2, PA-06): ocupan agenda todos los
     * estados salvo {@code CANCELADO} y {@code EXPIRADO}. Solo para disponibilidad orientativa y listados: la
     * garantía de no superposición vive en la base (regla dura #8).
     */
    public boolean ocupaAgenda() {
        return this != CANCELADO && this != EXPIRADO;
    }
}
