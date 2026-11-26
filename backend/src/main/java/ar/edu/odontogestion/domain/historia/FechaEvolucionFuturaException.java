package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** La fecha de la consulta no puede ser posterior a hoy en el consultorio (422). Mensaje fijo. */
public class FechaEvolucionFuturaException extends ReglaNegocioException {

    public FechaEvolucionFuturaException() {
        super("La fecha de la evolución no puede ser futura.");
    }

    @Override
    public String codigo() {
        return "fecha-evolucion-futura";
    }
}
