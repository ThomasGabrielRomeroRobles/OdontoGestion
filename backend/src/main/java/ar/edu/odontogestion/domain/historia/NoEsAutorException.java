package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.error.ProhibidoException;

/** Solo el autor de una evolución puede editarla (403). Mensaje fijo. */
public class NoEsAutorException extends ProhibidoException {

    public NoEsAutorException() {
        super("Solo el autor de la evolución puede editarla.");
    }

    @Override
    public String codigo() {
        return "no-es-autor";
    }
}
