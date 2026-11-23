package ar.edu.odontogestion.domain.error;

/**
 * Exceso de solicitudes públicas (429, C-09 D4): límite por IP, por DNI, por email o máximo de pendientes. El
 * mensaje es fijo y el mismo para todos los límites, así la respuesta no revela cuál se alcanzó ni si un DNI o
 * email tuvo actividad (RN-PU-07).
 */
public class DemasiadasSolicitudesException extends DemasiadosIntentosException {

    static final String MENSAJE = "Demasiadas solicitudes. Intente nuevamente más tarde.";

    public DemasiadasSolicitudesException(double segundosRestantes) {
        super(MENSAJE, segundosRestantes);
    }

    @Override
    public String codigo() {
        return "demasiadas-solicitudes";
    }
}
