package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.DatosHorario;
import jakarta.validation.constraints.NotNull;

/**
 * Solo {@code @NotNull} (400 si falta). Las horas son {@code String}: si fueran {@code LocalTime}, un valor mal
 * formado cortaría en Jackson con 400 sin campo; así el servicio responde 422 en el campo (design D11).
 */
public record HorarioRequest(
        @NotNull(message = "Es obligatorio.") Integer diaSemana,
        @NotNull(message = "Es obligatorio.") String horaDesde,
        @NotNull(message = "Es obligatorio.") String horaHasta) {

    DatosHorario aDatos() {
        return new DatosHorario(diaSemana, horaDesde, horaHasta);
    }
}
