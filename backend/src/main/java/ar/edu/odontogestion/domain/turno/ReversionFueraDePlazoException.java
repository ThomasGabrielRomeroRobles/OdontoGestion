package ar.edu.odontogestion.domain.turno;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** Se quiso revertir una ausencia marcada hace 24 horas o más (422, RN-AS-06). Mensaje fijo. */
public class ReversionFueraDePlazoException extends ReglaNegocioException {

    public ReversionFueraDePlazoException() {
        super("La ausencia solo se puede revertir dentro de las 24 horas desde que se marcó.");
    }

    @Override
    public String codigo() {
        return "reversion-fuera-de-plazo";
    }
}
