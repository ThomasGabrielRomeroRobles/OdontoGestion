package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.DatosBloqueo;
import jakarta.validation.constraints.NotNull;

/**
 * Solo {@code @NotNull} (400 si falta). {@code desde}/{@code hasta} son {@code String}: si fueran {@code Instant},
 * una fecha mal formada cortaría en Jackson con 400 sin campo; así el servicio responde 422 en el campo (D11).
 * {@code motivo} es opcional: ausente o {@code null} se guarda {@code ""} (D12).
 */
public record BloqueoRequest(
        @NotNull(message = "Es obligatorio.") String desde,
        @NotNull(message = "Es obligatorio.") String hasta,
        String motivo) {

    DatosBloqueo aDatos() {
        return new DatosBloqueo(desde, hasta, motivo);
    }
}
