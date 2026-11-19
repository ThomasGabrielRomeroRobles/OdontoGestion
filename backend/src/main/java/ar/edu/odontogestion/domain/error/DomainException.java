package ar.edu.odontogestion.domain.error;

/** Raíz de las excepciones de dominio; la capa API las traduce a códigos HTTP coherentes. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** Código estable, usado para armar el {@code type} del ProblemDetail. */
    public abstract String codigo();
}
