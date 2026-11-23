package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.agenda.DisponibilidadService;
import ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Puerta de entrada de los comandos de turnos para la API (design D6). <b>Sin {@code @Transactional}</b> a
 * propósito: tras un {@code 23P01} la transacción de escritura queda abortada y no se puede consultar nada en ella,
 * así que las alternativas del {@code 409} se calculan acá, en una transacción de solo lectura nueva.
 */
@Service
public class TurnoComandos {

    /** Máximo de alternativas que acompañan a un conflicto (D1). */
    static final int MAX_ALTERNATIVAS = 3;

    private final ReservaTurnoService reservas;
    private final GestionTurnoService gestion;
    private final DisponibilidadService disponibilidad;

    public TurnoComandos(ReservaTurnoService reservas, GestionTurnoService gestion,
            DisponibilidadService disponibilidad) {
        this.reservas = reservas;
        this.gestion = gestion;
        this.disponibilidad = disponibilidad;
    }

    public TurnoDto crear(Long pacienteId, Long prestacionId, Instant inicio) {
        try {
            return reservas.reservar(SolicitudReserva.delOdontologo(pacienteId, prestacionId, inicio));
        } catch (HuecoNoDisponibleException e) {
            throw e.conAlternativas(disponibilidad.alternativas(prestacionId, inicio, MAX_ALTERNATIVAS));
        }
    }

    public TurnoDto mover(Long turnoId, Instant nuevoInicio) {
        try {
            return gestion.mover(turnoId, nuevoInicio);
        } catch (HuecoNoDisponibleException e) {
            Long prestacionId = gestion.prestacionDe(turnoId);
            throw e.conAlternativas(disponibilidad.alternativas(prestacionId, nuevoInicio, MAX_ALTERNATIVAS));
        }
    }

    public TurnoDto cancelar(Long turnoId, String motivo) {
        return gestion.cancelar(turnoId, motivo);
    }

    public TurnoDto marcarAtendido(Long turnoId) {
        return gestion.marcarAtendido(turnoId);
    }

    public TurnoDto marcarAusente(Long turnoId) {
        return gestion.marcarAusente(turnoId);
    }

    public TurnoDto revertirAusencia(Long turnoId) {
        return gestion.revertirAusencia(turnoId);
    }
}
