package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.BloqueoVista;
import java.time.Instant;

/** Instantes ISO-8601 en UTC ({@code ...Z}); {@code motivo} siempre texto, {@code ""} si no hay. */
public record BloqueoResponse(Long id, Instant desde, Instant hasta, String motivo) {

    static BloqueoResponse from(BloqueoVista v) {
        return new BloqueoResponse(v.id(), v.desde(), v.hasta(), v.motivo());
    }
}
