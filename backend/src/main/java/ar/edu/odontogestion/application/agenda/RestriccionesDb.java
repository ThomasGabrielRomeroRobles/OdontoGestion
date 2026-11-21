package ar.edu.odontogestion.application.agenda;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Identifica qué restricción con nombre de la base provocó una {@link DataIntegrityViolationException}
 * (design.md D3). Revisa toda la cadena de causas: el nombre que extrae Hibernate y, si no lo trae (p. ej. para
 * {@code EXCLUDE}), el mensaje de PostgreSQL, que siempre lo incluye entre comillas.
 */
final class RestriccionesDb {

    private RestriccionesDb() {
    }

    static boolean viola(DataIntegrityViolationException ex, String restriccion) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cve
                    && restriccion.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            String mensaje = causa.getMessage();
            if (mensaje != null && mensaje.contains("\"" + restriccion + "\"")) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }
}
