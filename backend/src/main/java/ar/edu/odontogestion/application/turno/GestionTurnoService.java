package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.turno.evento.AusenciaRevertida;
import ar.edu.odontogestion.domain.turno.evento.HuecoLiberado;
import ar.edu.odontogestion.domain.turno.evento.TurnoAusente;
import ar.edu.odontogestion.domain.turno.evento.TurnoCancelado;
import ar.edu.odontogestion.domain.turno.evento.TurnoMovido;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambios de un turno existente por el odontólogo: mover, cancelar, marcar atendido (design D9) y marcar o revertir
 * una ausencia (C-08 design D4). Cada operación
 * carga el turno del consultorio actual (404 si no existe o es de otro), aplica el método de dominio y hace
 * {@code flush}; dos operaciones concurrentes sobre el mismo turno se resuelven con {@code @Version}
 * ({@code 409 modificacion-concurrente}) y un choque de agenda lo decide la base ({@code 409 hueco-no-disponible}).
 */
@Service
public class GestionTurnoService {

    private static final Logger log = LoggerFactory.getLogger(GestionTurnoService.class);

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final TurnoRepository turnos;
    private final ValidadorHorarioTurno validador;
    private final TraductorViolacionAgenda traductor;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final PacienteRepository pacientes;

    public GestionTurnoService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            TurnoRepository turnos, ValidadorHorarioTurno validador, TraductorViolacionAgenda traductor,
            ApplicationEventPublisher eventos, Clock clock, PacienteRepository pacientes) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.turnos = turnos;
        this.validador = validador;
        this.traductor = traductor;
        this.eventos = eventos;
        this.clock = clock;
        this.pacientes = pacientes;
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

    /**
     * Marca el turno ausente pasada la tolerancia del consultorio (RN-AS-01) y suma una ausencia al paciente
     * (RN-AS-02) en la misma transacción. Publica {@link TurnoAusente} (encola {@code AUSENTE_REPROGRAMAR}); no hay
     * hueco liberado porque el turno sigue ocupando su rango (RN-AS-04).
     */
    @Transactional
    public TurnoDto marcarAusente(Long turnoId) {
        Long consultorioId = consultorioActual.id();
        Turno turno = cargar(turnoId, consultorioId);
        int tolerancia = consultorios.findById(consultorioId).orElseThrow().getParametros().toleranciaAusenteMin();
        turno.marcarAusente(clock.instant(), tolerancia);
        Turno guardado = turnos.saveAndFlush(turno);
        Long pacienteId = guardado.getPaciente().getId();
        pacientes.incrementarAusencias(pacienteId);
        eventos.publishEvent(new TurnoAusente(guardado.getId()));
        return TurnoDto.de(recargar(turnoId, consultorioId));
    }

    /**
     * Revierte una ausencia dentro de las 24 h (RN-AS-06): el turno vuelve a su estado previo y el paciente pierde
     * una ausencia. Publica {@link AusenciaRevertida} (descarta el aviso pendiente).
     */
    @Transactional
    public TurnoDto revertirAusencia(Long turnoId) {
        Long consultorioId = consultorioActual.id();
        Turno turno = cargar(turnoId, consultorioId);
        turno.revertirAusencia(clock.instant());
        Turno guardado = turnos.saveAndFlush(turno);
        Long pacienteId = guardado.getPaciente().getId();
        if (pacientes.decrementarAusencias(pacienteId) == 0) {
            log.warn("El contador de ausencias del paciente {} ya estaba en cero al revertir el turno {}",
                    pacienteId, turnoId);
        }
        eventos.publishEvent(new AusenciaRevertida(guardado.getId()));
        return TurnoDto.de(recargar(turnoId, consultorioId));
    }

    /** Prestación del turno, para calcular alternativas tras un conflicto (D6). */
    @Transactional(readOnly = true)
    public Long prestacionDe(Long turnoId) {
        return cargar(turnoId, consultorioActual.id()).getPrestacion().getId();
    }

    /** Relee el turno tras un {@code UPDATE} del contador, que limpió el contexto de persistencia (design D3). */
    private Turno recargar(Long turnoId, Long consultorioId) {
        return cargar(turnoId, consultorioId);
    }

    private Turno cargar(Long turnoId, Long consultorioId) {
        return turnos.findByIdAndConsultorio_Id(turnoId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Turno no encontrado"));
    }
}
