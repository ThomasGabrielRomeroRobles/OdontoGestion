package ar.edu.odontogestion.domain.turno.evento;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;

/** Se creó un turno (design D4). Solo ids y estado: el listener consulta lo que necesite dentro de la transacción. */
public record TurnoCreado(long turnoId, long consultorioId, EstadoTurno estado, OrigenTurno origen) {
}
