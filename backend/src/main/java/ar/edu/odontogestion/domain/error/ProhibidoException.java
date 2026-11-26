package ar.edu.odontogestion.domain.error;

/** Operación no permitida para el usuario con sesión aunque el recurso exista (403). Mensaje fijo. */
public class ProhibidoException extends DomainException {

    public ProhibidoException(String message) {
        super(message);
    }

    @Override
    public String codigo() {
        return "prohibido";
    }
}
