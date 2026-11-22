package ar.edu.odontogestion.application.turno;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.turno.CanceladoPor;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import java.time.Instant;
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
    private final Clock clock;

    public GestionTurnoService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            TurnoRepository turnos, ValidadorHorarioTurno validador, TraductorViolacionAgenda traductor,
            Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.turnos = turnos;
        this.validador = validador;
        this.traductor = traductor;
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

        turno.mover(nuevoInicio);
        return TurnoDto.de(traductor.ejecutar(() -> turnos.saveAndFlush(turno)));
    }

    @Transactional
    public TurnoDto cancelar(Long turnoId, String motivo) {
        Turno turno = cargar(turnoId, consultorioActual.id());
        turno.cancelar(CanceladoPor.ODONTOLOGO, motivo);
        return TurnoDto.de(turnos.saveAndFlush(turno));
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
