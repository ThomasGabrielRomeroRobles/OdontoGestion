package ar.edu.odontogestion.domain.historia;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;

/** La pieza no pertenece a las 32 permanentes FDI (422). Mensaje fijo, sin el valor recibido. */
public class PiezaFdiInvalidaException extends ReglaNegocioException {

    public PiezaFdiInvalidaException() {
        super("La pieza dental debe ser una de las 32 piezas permanentes en numeración FDI.");
    }

    @Override
    public String codigo() {
        return "pieza-fdi-invalida";
    }
}
