package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Núcleo de reserva reutilizable (design D5): lo usan {@code POST /turnos} (C-07), la solicitud pública (C-09) y la
 * aceptación de un aviso (C-10). Valida horario y recursos, pero <b>el conflicto de agenda lo decide la base</b>
 * ({@code ex_turno_sin_superposicion}, regla dura #8): nunca un chequeo previo en Java.
 */
@Service
public class ReservaTurnoService {

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final PacienteRepository pacientes;
    private final PrestacionRepository prestaciones;
    private final TurnoRepository turnos;
    private final ValidadorHorarioTurno validador;
    private final TraductorViolacionAgenda traductor;
    private final Clock clock;

    public ReservaTurnoService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            PacienteRepository pacientes, PrestacionRepository prestaciones, TurnoRepository turnos,
            ValidadorHorarioTurno validador, TraductorViolacionAgenda traductor, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.turnos = turnos;
        this.validador = validador;
        this.traductor = traductor;
        this.clock = clock;
    }

    /**
     * @throws RecursoNoEncontradoException paciente o prestación inexistentes o de otro consultorio (404)
     * @throws ValidacionDominioException prestación inactiva, paciente dado de baja u horario inválido (422)
     * @throws ar.edu.odontogestion.domain.turno.HuecoNoDisponibleException el intervalo ya está ocupado (409)
     */
    @Transactional
    public TurnoDto reservar(SolicitudReserva solicitud) {
        Long consultorioId = consultorioActual.id();
        // Primero se liberan las pendientes vencidas (D8); si la reserva falla, esto se revierte con ella.
        turnos.expirarPendientesVencidas(consultorioId, clock.instant());
        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        Prestacion prestacion = prestaciones.findByIdAndConsultorio_Id(solicitud.prestacionId(), consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestación no encontrada"));
        Paciente paciente = pacientes.findByIdAndConsultorioId(solicitud.pacienteId(), consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado"));
        if (!prestacion.isActiva()) {
            throw new ValidacionDominioException("prestacionId", "La prestación está desactivada.");
        }
        if (!paciente.isActivo()) {
            throw new ValidacionDominioException("pacienteId", "El paciente está dado de baja.");
        }
        validador.validar(consultorio, solicitud.inicio(), prestacion.getDuracionMin(), solicitud.politica());

        Turno turno = solicitud.estadoInicial() == EstadoTurno.RESERVADO
                ? Turno.reservado(consultorio, paciente, prestacion, solicitud.inicio(), solicitud.origen())
                : Turno.pendiente(consultorio, paciente, prestacion, solicitud.inicio(), solicitud.origen(),
                        clock.instant().plus(Duration.ofMinutes(consultorio.getParametros().vencimientoPendienteMin())));
        return TurnoDto.de(traductor.ejecutar(() -> turnos.saveAndFlush(turno)));
    }
}
