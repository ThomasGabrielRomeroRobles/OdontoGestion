package ar.edu.odontogestion.domain.error;

/** Conflicto de estado o de agenda (409). */
public class ConflictoException extends DomainException {

    public ConflictoException(String message) {
        super(message);
    }

    @Override
    public String codigo() {
        return "conflicto";
    }
}
