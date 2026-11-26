package ar.edu.odontogestion.api.historia;

import ar.edu.odontogestion.domain.historia.EstadoPieza;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Estado de una pieza; un estado fuera del enum falla al leer el cuerpo (400). */
public record PiezaRequest(@NotNull EstadoPieza estado, @Size(max = 500) String observacion) {
}
