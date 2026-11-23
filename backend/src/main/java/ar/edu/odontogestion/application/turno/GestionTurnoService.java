package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.domain.turno.evento.TurnoMovido;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambios de un turno existente por el odontólogo: mover, cancelar y marcar atendido (design D9). Cada operación
 * carga el turno del consultorio actual (404 si no existe o es de otro), aplica el método de dominio y hace
 * {@code flush}; dos operaciones concurrentes sobre el mismo turno se resuelven con {@code @Version}
 * ({@code 409 modificacion-concurrente}) y un choque de agenda lo decide la base ({@code 409 hueco-no-disponible}).
 */
@Service
public class GestionTurnoService {

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final TurnoRepository turnos;
    private final ValidadorHorarioTurno validador;
    private final TraductorViolacionAgenda traductor;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public GestionTurnoService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            TurnoRepository turnos, ValidadorHorarioTurno validador, TraductorViolacionAgenda traductor,
            ApplicationEventPublisher eventos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.turnos = turnos;
        this.validador = validador;
        this.traductor = traductor;
        this.eventos = eventos;
        this.clock = clock;
    }

    /**
     * Mueve un turno {@code RESERVADO}/{@code CONFIRMADO} a otro inicio válido conservando duración y estado. El
     * rango anterior se libera en el mismo commit (es un {@code UPDATE} del mismo registro).
     */
    @Transactional
    public TurnoDto mover(Long turnoId, Instant nuevoInicio) {
        Long consultorioId = consultorioActual.id();
        turnos.expirarPendientesVencidas(consultorioId, clock.instant());
        Turno turno = cargar(turnoId, consultorioId);
        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        validador.validar(consultorio, nuevoInicio, turno.getPrestacion().getDuracionMin(),
                PoliticaAnticipacion.odontologo());

        Hueco anterior = turno.mover(nuevoInicio);
        Turno guardado = traductor.ejecutar(() -> turnos.saveAndFlush(turno));
        // Se publica el rango anterior completo aunque el nuevo lo solape: C-10 recalcula la disponibilidad (D10).
        eventos.publishEvent(new TurnoMovido(guardado.getId(), anterior.inicio(), anterior.fin(),
                guardado.getInicio(), guardado.getFin()));
        eventos.publishEvent(HuecoLiberado.de(guardado, anterior));
        return TurnoDto.de(guardado);
    }

    @Transactional
    public TurnoDto cancelar(Long turnoId, String motivo) {
        Turno turno = cargar(turnoId, consultorioActual.id());
        Hueco liberado = new Hueco(turno.getInicio(), turno.getFin());
        turno.cancelar(CanceladoPor.ODONTOLOGO, motivo);
        Turno guardado = turnos.saveAndFlush(turno);
        eventos.publishEvent(new TurnoCancelado(guardado.getId(), CanceladoPor.ODONTOLOGO));
        eventos.publishEvent(HuecoLiberado.de(guardado, liberado));
        return TurnoDto.de(guardado);
    }

    @Transactional
    public TurnoDto marcarAtendido(Long turnoId) {
        Turno turno = cargar(turnoId, consultorioActual.id());
        turno.marcarAtendido();
        return TurnoDto.de(turnos.saveAndFlush(turno));
    }

    /** Prestación del turno, para calcular alternativas tras un conflicto (D6). */
    @Transactional(readOnly = true)
    public Long prestacionDe(Long turnoId) {
        return cargar(turnoId, consultorioActual.id()).getPrestacion().getId();
    }

    private Turno cargar(Long turnoId, Long consultorioId) {
        return turnos.findByIdAndConsultorio_Id(turnoId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Turno no encontrado"));
    }
}
