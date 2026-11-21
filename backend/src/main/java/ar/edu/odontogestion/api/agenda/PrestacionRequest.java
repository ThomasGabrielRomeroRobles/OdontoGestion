package ar.edu.odontogestion.api.agenda;

import ar.edu.odontogestion.application.agenda.DatosPrestacion;
import jakarta.validation.constraints.NotNull;

/** Solo {@code @NotNull} (400 si falta); vacío, tamaño y granularidad los valida el servicio (422, design D11). */
public record PrestacionRequest(
        @NotNull(message = "Es obligatorio.") String nombre,
        @NotNull(message = "Es obligatorio.") Integer duracionMin) {

    DatosPrestacion aDatos() {
        return new DatosPrestacion(nombre, duracionMin);
    }
}
