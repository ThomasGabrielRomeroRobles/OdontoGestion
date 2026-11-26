package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.application.historia.DatosEvolucion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Alta de evolución: {@code fecha} nula = hoy en el consultorio; {@code turnoId} opcional. */
public record EvolucionRequest(@NotBlank @Size(max = 4000) String nota, LocalDate fecha, Long turnoId) {

    DatosEvolucion aDatos() {
        return new DatosEvolucion(nota, fecha, turnoId);
    }
}
