package ar.edu.odontogestion.domain.turno.evento;

import java.time.Instant;

/**
 * Quedó libre el rango {@code [inicio, fin)} al cancelar o mover un turno (design D4/D10). C-10 lo consume para la
 * lista de espera. La expiración de pendientes no lo emite (D8).
 */
public record HuecoLiberado(long consultorioId, long turnoOrigenId, long prestacionId, Instant inicio, Instant fin) {
}
