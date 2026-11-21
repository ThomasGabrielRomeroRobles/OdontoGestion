package ar.edu.odontogestion.domain.error;

import java.util.List;

/** Regla de negocio incumplida con errores por campo (422 con {@code errors}). */
public class ValidacionDominioException extends ReglaNegocioException {

    private final List<ErrorCampo> errores;

    public ValidacionDominioException(List<ErrorCampo> errores) {
        super("La solicitud no cumple las reglas de negocio.");
        this.errores = List.copyOf(errores);
    }

    public ValidacionDominioException(String campo, String mensaje) {
        this(List.of(new ErrorCampo(campo, mensaje)));
    }

    public List<ErrorCampo> errores() {
        return errores;
    }
}
