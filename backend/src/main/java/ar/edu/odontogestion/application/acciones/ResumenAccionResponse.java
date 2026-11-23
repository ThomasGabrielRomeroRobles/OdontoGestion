package ar.edu.odontogestion.application.acciones;

import ar.edu.odontogestion.domain.listaespera.EstadoEntrada;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Lo que ve el paciente de su enlace (C-09 D1, C-10 D3): la acción, si puede ejecutarse ahora y un resumen del turno
 * o de la entrada de lista de espera. Nunca datos del paciente ni ids. {@code venceEn} es {@code null} tras ejecutar
 * (el token ya está usado); {@code limiteCancelacion} solo viene en {@code CANCELAR_TURNO}. Exactamente uno de
 * {@code turno} y {@code entrada} viene informado (el otro es {@code null}).
 */
public record ResumenAccionResponse(PropositoToken accion, Instant venceEn, boolean disponible, Motivo motivo,
        Instant limiteCancelacion, TurnoResumen turno, EntradaResumen entrada) {

    /** Por qué la acción no está disponible. */
    public enum Motivo {
        ESTADO_NO_PERMITE, FUERA_DE_LIMITE
    }

    public record TurnoResumen(String consultorio, String prestacion, Instant inicio, Instant fin,
            EstadoTurno estado) {
    }

    /** Entrada de lista de espera ({@code CONFIRMAR_LISTA_ESPERA}); sin datos del paciente. */
    public record EntradaResumen(String consultorio, String prestacion, LocalDate fechaDesde, LocalDate fechaHasta,
            EstadoEntrada estado) {
    }
}
