package ar.edu.odontogestion.domain.turno.evento;

import ar.edu.odontogestion.domain.turno.CanceladoPor;

/** Se canceló un turno. */
public record TurnoCancelado(long turnoId, CanceladoPor canceladoPor) {
}
