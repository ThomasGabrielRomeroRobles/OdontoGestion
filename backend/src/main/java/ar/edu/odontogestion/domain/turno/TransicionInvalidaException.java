package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** Cambio de estado no previsto por RN-TU-02 (422). El mensaje solo nombra estados, nunca datos del paciente. */
public class TransicionInvalidaException extends ReglaNegocioException {

    private final EstadoTurno desde;
    private final EstadoTurno hacia;

    public TransicionInvalidaException(EstadoTurno desde, EstadoTurno hacia) {
        super("No se puede pasar un turno de " + desde + " a " + hacia + ".");
        this.desde = desde;
        this.hacia = hacia;
    }

    public EstadoTurno desde() {
        return desde;
    }

    public EstadoTurno hacia() {
        return hacia;
    }

    @Override
    public String codigo() {
        return "transicion-invalida";
    }
}
