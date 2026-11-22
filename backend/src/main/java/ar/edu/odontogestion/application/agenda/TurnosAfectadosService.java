package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.turno.TurnoDto;
import ar.edu.odontogestion.domain.agenda.BloqueoAgenda;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turnos que un bloqueo de agenda pisa, para reubicarlos a mano (US-012). Crear un bloqueo nunca cancela ni mueve
 * turnos: esta consulta solo informa.
 */
@Service
public class TurnosAfectadosService {

    private final ConsultorioActual consultorioActual;
    private final BloqueoAgendaRepository bloqueos;
    private final TurnoRepository turnos;
    private final Clock clock;

    public TurnosAfectadosService(ConsultorioActual consultorioActual, BloqueoAgendaRepository bloqueos,
            TurnoRepository turnos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TurnoDto> afectadosPor(Long bloqueoId) {
        Long consultorioId = consultorioActual.id();
        BloqueoAgenda bloqueo = bloqueos.findByIdAndConsultorio_Id(bloqueoId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Bloqueo no encontrado"));
        return turnos.ocupantesQueSolapan(consultorioId, bloqueo.getDesde(), bloqueo.getHasta(), clock.instant())
                .stream().map(TurnoDto::de).toList();
    }
}
