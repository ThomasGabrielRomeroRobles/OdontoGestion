package ar.edu.odontogestion.domain.error;

/** Exceso de intentos (429): el cliente debe esperar {@link #retryAfterSegundos()} segundos. */
public class DemasiadosIntentosException extends DomainException {

    private final double segundosRestantes;

    public DemasiadosIntentosException(double segundosRestantes) {
        this("Demasiados intentos. Intente nuevamente más tarde.", segundosRestantes);
    }

    /** Para las subclases con un mensaje fijo propio (nunca con datos del cliente). */
    protected DemasiadosIntentosException(String mensaje, double segundosRestantes) {
        super(mensaje);
        this.segundosRestantes = segundosRestantes;
    }

    /** Espera para la cabecera {@code Retry-After}: redondeada hacia arriba y nunca menor que 1. */
    public long retryAfterSegundos() {
        return Math.max(1L, (long) Math.ceil(segundosRestantes));
    }

    @Override
    public String codigo() {
        return "demasiados-intentos";
    }
}
