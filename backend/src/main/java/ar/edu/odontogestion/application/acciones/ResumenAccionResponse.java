package ar.edu.odontogestion.application.acciones;

import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;

/**
 * Lo que ve el paciente de su enlace (C-09 D1): la acción, si puede ejecutarse ahora y un resumen del turno. Nunca
 * datos del paciente ni ids. {@code venceEn} es {@code null} tras ejecutar (el token ya está usado);
 * {@code limiteCancelacion} solo viene en {@code CANCELAR_TURNO}.
 */
public record ResumenAccionResponse(PropositoToken accion, Instant venceEn, boolean disponible, Motivo motivo,
        Instant limiteCancelacion, TurnoResumen turno) {

    /** Por qué la acción no está disponible. */
    public enum Motivo {
        ESTADO_NO_PERMITE, FUERA_DE_LIMITE
    }

    public record TurnoResumen(String consultorio, String prestacion, Instant inicio, Instant fin,
            EstadoTurno estado) {
    }
}
