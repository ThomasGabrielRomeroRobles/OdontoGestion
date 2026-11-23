package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** Cambio de estado no previsto por RN-TU-02 (422). El mensaje solo nombra estados, nunca datos del paciente. */
public class TransicionInvalidaException extends ReglaNegocioException {

    private final EstadoTurno desde;
    private final EstadoTurno hacia;

    public TransicionInvalidaException(EstadoTurno desde, EstadoTurno hacia) {
        this("No se puede pasar un turno de " + desde + " a " + hacia + ".", desde, hacia);
    }

    private TransicionInvalidaException(String mensaje, EstadoTurno desde, EstadoTurno hacia) {
        super(mensaje);
        this.desde = desde;
        this.hacia = hacia;
    }

    /** Solo {@code RESERVADO} y {@code CONFIRMADO} pueden cambiar de horario. */
    public static TransicionInvalidaException noMovible(EstadoTurno actual) {
        return new TransicionInvalidaException("No se puede mover un turno en estado " + actual + ".", actual,
                null);
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
