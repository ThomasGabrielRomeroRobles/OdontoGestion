package ar.edu.odontogestion.domain.error;

/** Regla de negocio incumplida (422). */
public class ReglaNegocioException extends DomainException {

    public ReglaNegocioException(String message) {
        super(message);
    }

    @Override
    public String codigo() {
        return "regla-de-negocio";
    }
}
