package ar.edu.odontogestion.api.turno;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** {@code PUT /api/v1/turnos/{id}/mover}: nuevo inicio; la duración se conserva. */
public record MoverTurnoRequest(@NotNull Instant inicio) {
}
