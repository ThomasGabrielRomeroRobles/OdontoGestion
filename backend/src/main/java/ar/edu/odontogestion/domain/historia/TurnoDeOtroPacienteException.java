package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/**
 * El turno indicado no es del paciente de la historia (422). Mismo código si el turno no existe en el consultorio,
 * para no distinguir existencia (C-05 design D6).
 */
public class TurnoDeOtroPacienteException extends ReglaNegocioException {

    public TurnoDeOtroPacienteException() {
        super("El turno indicado no corresponde a este paciente.");
    }

    @Override
    public String codigo() {
        return "turno-de-otro-paciente";
    }
}
