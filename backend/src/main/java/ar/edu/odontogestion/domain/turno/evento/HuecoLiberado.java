package ar.edu.odontogestion.domain.turno.evento;

import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.turno.Turno;
import java.time.Instant;

/**
 * Quedó libre el rango {@code [inicio, fin)} al cancelar o mover un turno (design D4/D10). C-10 lo consume para la
 * lista de espera. La expiración de pendientes no lo emite (D8).
 */
public record HuecoLiberado(long consultorioId, long turnoOrigenId, long prestacionId, Instant inicio, Instant fin) {

    /** Evento del turno {@code turno} que dejó libre {@code rango} (puede no ser su rango actual, p. ej. al mover). */
    public static HuecoLiberado de(Turno turno, Hueco rango) {
        return new HuecoLiberado(turno.getConsultorio().getId(), turno.getId(), turno.getPrestacion().getId(),
                rango.inicio(), rango.fin());
    }
}
