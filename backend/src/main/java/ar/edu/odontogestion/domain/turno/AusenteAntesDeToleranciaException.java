package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/**
 * Se quiso marcar ausente antes de {@code inicio + tolerancia} (422, RN-AS-01). Mensaje fijo: sin fechas ni datos del
 * paciente.
 */
public class AusenteAntesDeToleranciaException extends ReglaNegocioException {

    public AusenteAntesDeToleranciaException() {
        super("Todavía no pasó la tolerancia desde el inicio del turno: no se puede marcar ausente.");
    }

    @Override
    public String codigo() {
        return "ausente-antes-de-tolerancia";
    }
}
