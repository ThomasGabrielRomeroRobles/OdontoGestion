package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import java.time.Instant;

/**
 * Turno tal como lo ve la agenda (design D1). Nunca incluye DNI, email ni teléfono del paciente ni datos clínicos
 * (regla dura #10). Se construye dentro de la transacción: las relaciones del turno son {@code LAZY}.
 */
public record TurnoDto(
        long id,
        Instant inicio,
        Instant fin,
        EstadoTurno estado,
        OrigenTurno origen,
        PacienteResumen paciente,
        PrestacionResumen prestacion,
        Instant pendienteHasta,
        CanceladoPor canceladoPor,
        String motivoCancelacion,
        long version,
        Instant ausenteMarcadoEn) {

    /** @param cantidadAusencias contador de ausencias del paciente (RN-AS-02); sin DNI, email ni teléfono */
    public record PacienteResumen(long id, String nombre, String apellido, int cantidadAusencias) {
    }

    public record PrestacionResumen(long id, String nombre) {
    }

    public static TurnoDto de(Turno t) {
        return new TurnoDto(t.getId(), t.getInicio(), t.getFin(), t.getEstado(), t.getOrigen(),
                new PacienteResumen(t.getPaciente().getId(), t.getPaciente().getNombre(),
                        t.getPaciente().getApellido(), t.getPaciente().getCantidadAusencias()),
                new PrestacionResumen(t.getPrestacion().getId(), t.getPrestacion().getNombre()),
                t.getPendienteHasta(), t.getCanceladoPor(), t.getMotivoCancelacion(), t.getVersion(),
                t.getAusenteMarcadoEn());
    }
}
