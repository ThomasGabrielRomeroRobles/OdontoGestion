package ar.edu.odontogestion.domain.listaespera;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** Cambio de estado no permitido de una entrada o un aviso (422). El mensaje solo nombra estados. */
public class TransicionListaEsperaInvalidaException extends ReglaNegocioException {

    public TransicionListaEsperaInvalidaException(String que, Enum<?> desde, Enum<?> hacia) {
        super("No se puede pasar " + que + " de " + desde + " a " + hacia + ".");
    }
}
