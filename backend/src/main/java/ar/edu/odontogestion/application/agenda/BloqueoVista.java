package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.domain.agenda.BloqueoAgenda;
import java.time.Instant;

/** Lectura de un bloqueo para la capa API; {@code motivo} nunca es {@code null}. */
public record BloqueoVista(Long id, Instant desde, Instant hasta, String motivo) {

    static BloqueoVista de(BloqueoAgenda b) {
        return new BloqueoVista(b.getId(), b.getDesde(), b.getHasta(), b.getMotivo());
    }
}
