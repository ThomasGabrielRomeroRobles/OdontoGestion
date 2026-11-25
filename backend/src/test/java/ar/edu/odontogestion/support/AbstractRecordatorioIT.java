package ar.edu.odontogestion.support;

import ar.edu.odontogestion.domain.turno.EstadoTurno;
import java.time.Instant;

/** Base de los ITs de recordatorios (C-11): turnos con {@code creado_en} fijado por JDBC (lo pone la base con now()). */
public abstract class AbstractRecordatorioIT extends AbstractTurnoIT {

    /** Inserta un turno de 30 segundos (sin solapes entre fixtures) con el {@code creado_en} indicado. */
    protected Long turno(Long paciente, Long prestacion, EstadoTurno estado, Instant inicio, Instant creadoEn) {
        Long id = insertarTurno(paciente, prestacion, estado, inicio, inicio.plusSeconds(30));
        jdbc.update("UPDATE turno SET creado_en = ?::timestamptz WHERE id = ?", creadoEn.toString(), id);
        return id;
    }

    /** Turno {@code RESERVADO} creado hace 48 h respecto del reloj actual: cumple RN-NO-09 para H hasta 48. */
    protected Long reservado(Long paciente, Long prestacion, Instant inicio) {
        return turno(paciente, prestacion, EstadoTurno.RESERVADO, inicio, reloj.instant().minusSeconds(48 * 3600));
    }

    protected int recordatoriosDe(Long turnoId) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE turno_id = ? AND tipo = 'RECORDATORIO'",
                Integer.class, turnoId);
    }
}
