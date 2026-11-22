package ar.edu.odontogestion.api.turno;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** {@code POST /api/v1/turnos}: el inicio es un instante ISO-8601 con zona (p. ej. {@code 2026-11-23T13:00:00Z}). */
public record CrearTurnoRequest(
        @NotNull Long pacienteId,
        @NotNull Long prestacionId,
        @NotNull Instant inicio) {
}
