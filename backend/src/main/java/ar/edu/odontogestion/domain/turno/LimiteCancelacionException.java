package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/**
 * El paciente quiso cancelar pasado el límite de horas antes del turno (422, RN-TU-03). Mensaje fijo: no incluye
 * fechas ni datos del turno.
 */
public class LimiteCancelacionException extends ReglaNegocioException {

    public LimiteCancelacionException() {
        super("Ya pasó el plazo para cancelar este turno por el enlace.");
    }

    @Override
    public String codigo() {
        return "limite-cancelacion";
    }
}
