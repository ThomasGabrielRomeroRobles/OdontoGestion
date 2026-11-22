package ar.edu.odontogestion.domain.turno.evento;

import java.time.Instant;

/** Un turno cambió de horario; incluye el rango anterior y el nuevo. */
public record TurnoMovido(long turnoId, Instant inicioAnterior, Instant finAnterior, Instant inicioNuevo,
        Instant finNuevo) {
}
