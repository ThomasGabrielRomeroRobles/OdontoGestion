package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.comun.RestriccionesDb;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.hibernate.JDBCException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Traduce la violación de {@code ex_turno_sin_superposicion} a {@link HuecoNoDisponibleException} (409). Es la
 * única decisión de conflicto de agenda (regla dura #8): la base. Se exige SQLState {@code 23P01} <b>y</b> el
 * nombre de la restricción, así que cualquier otra violación (una FK, otra exclusión) se relanza sin traducir
 * (design D5/D9). Lo comparten la reserva, el movimiento y, más adelante, C-09 y C-10.
 */
@Component
public class TraductorViolacionAgenda {

    static final String EX_TURNO_SIN_SUPERPOSICION = "ex_turno_sin_superposicion";
    private static final String SQLSTATE_EXCLUSION = "23P01";
    private static final String SQLSTATE_DEADLOCK = "40P01";

    public <T> T ejecutar(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataIntegrityViolationException ex) {
            if (esConflictoDeAgenda(ex)) {
                throw new HuecoNoDisponibleException();
            }
            throw ex;
        } catch (PessimisticLockingFailureException ex) {
            if (esDeadlockDeLaExclusion(ex)) {
                throw new HuecoNoDisponibleException();
            }
            throw ex;
        }
    }

    private static boolean esConflictoDeAgenda(DataIntegrityViolationException ex) {
        return tieneSqlState(ex, SQLSTATE_EXCLUSION) && RestriccionesDb.viola(ex, EX_TURNO_SIN_SUPERPOSICION);
    }

    /**
     * Dos inserciones concurrentes que se superponen se esperan mutuamente al chequear la exclusión: PostgreSQL
     * aborta a una con {@code 40P01} (deadlock detectado) en lugar de {@code 23P01}. Es el mismo conflicto de
     * agenda: la perdedora es un 409, la otra transacción conserva el hueco. Solo se traduce si el contexto del
     * error es el chequeo de exclusión sobre la relación {@code turno}.
     */
    private static boolean esDeadlockDeLaExclusion(PessimisticLockingFailureException ex) {
        if (!tieneSqlState(ex, SQLSTATE_DEADLOCK)) {
            return false;
        }
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            String mensaje = causa.getMessage();
            if (mensaje != null && mensaje.contains("exclusion constraint")
                    && mensaje.contains("relation \"turno\"")) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }

    private static boolean tieneSqlState(Throwable raiz, String sqlState) {
        for (Throwable causa = raiz; causa != null; causa = causa.getCause()) {
            String actual = causa instanceof SQLException sql ? sql.getSQLState()
                    : causa instanceof JDBCException jdbc ? jdbc.getSQLState() : null;
            if (sqlState.equals(actual)) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }
}
