package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** La ventana de edición de la evolución ya venció (422, C-05 design D5). Mensaje fijo. */
public class EvolucionFueraDeVentanaException extends ReglaNegocioException {

    public EvolucionFueraDeVentanaException() {
        super("La evolución ya no se puede editar: venció la ventana de edición.");
    }

    @Override
    public String codigo() {
        return "evolucion-fuera-de-ventana";
    }
}
