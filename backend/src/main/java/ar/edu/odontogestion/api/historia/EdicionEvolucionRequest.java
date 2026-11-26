package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.application.historia.DatosEvolucion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Edición de evolución: {@code fecha} nula conserva la actual. El turno asociado no se cambia. */
public record EdicionEvolucionRequest(@NotBlank @Size(max = 4000) String nota, LocalDate fecha) {

    DatosEvolucion aDatos() {
        return new DatosEvolucion(nota, fecha, null);
    }
}
