package ar.edu.odontogestion.domain.listaespera;

import ar.edu.odontogestion.domain.error.ConflictoException;

/** El aviso ya no se puede aceptar: otro lo tomó, se cerró o la entrada dejó de estar activa (409). */
public class AvisoNoDisponibleException extends ConflictoException {

    public AvisoNoDisponibleException() {
        super("El horario ya no está disponible.");
    }

    @Override
    public String codigo() {
        return "aviso-no-disponible";
    }
}
