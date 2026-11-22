package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.domain.agenda.Hueco;
import java.time.Instant;

/** Hueco libre {@code [inicio, fin)} en UTC (design D1). */
public record HuecoDto(Instant inicio, Instant fin) {

    public static HuecoDto de(Hueco h) {
        return new HuecoDto(h.inicio(), h.fin());
    }
}
