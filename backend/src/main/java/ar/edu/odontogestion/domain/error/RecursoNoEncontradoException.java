package ar.edu.odontogestion.domain.error;

/** Recurso inexistente (404). */
public class RecursoNoEncontradoException extends DomainException {

    public RecursoNoEncontradoException(String message) {
        super(message);
    }

    @Override
    public String codigo() {
        return "recurso-no-encontrado";
    }
}
