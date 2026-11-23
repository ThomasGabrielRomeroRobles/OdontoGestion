package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import java.time.Instant;

/**
 * Pedido al núcleo de reserva (design D5). {@code estadoInicial} es {@code RESERVADO} o
 * {@code PENDIENTE_CONFIRMACION}; la política decide la anticipación mínima exigida.
 */
public record SolicitudReserva(Long pacienteId, Long prestacionId, Instant inicio, OrigenTurno origen,
        EstadoTurno estadoInicial, PoliticaAnticipacion politica) {

    public SolicitudReserva {
        if (estadoInicial != EstadoTurno.RESERVADO && estadoInicial != EstadoTurno.PENDIENTE_CONFIRMACION) {
            throw new IllegalArgumentException("Un turno nace RESERVADO o PENDIENTE_CONFIRMACION");
        }
    }

    /** {@code POST /api/v1/turnos}: reservado, origen {@code ODONTOLOGO}, sin anticipación mínima. */
    public static SolicitudReserva delOdontologo(Long pacienteId, Long prestacionId, Instant inicio) {
        return new SolicitudReserva(pacienteId, prestacionId, inicio, OrigenTurno.ODONTOLOGO,
                EstadoTurno.RESERVADO, PoliticaAnticipacion.odontologo());
    }
}
