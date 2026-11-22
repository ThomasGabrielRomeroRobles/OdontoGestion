package ar.edu.odontogestion.domain.agenda;

import java.time.Instant;

/** Intervalo libre (o intervalo anterior de un turno movido) {@code [inicio, fin)} en UTC. */
public record Hueco(Instant inicio, Instant fin) {

    public Hueco {
        if (inicio == null || fin == null || !inicio.isBefore(fin)) {
            throw new IllegalArgumentException("Un hueco requiere inicio anterior a fin");
        }
    }
}
