package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.turno.TurnoDto;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listados de la agenda del odontólogo (solo lectura). Las fechas son locales del consultorio, inclusivas. */
@Service
public class AgendaConsultaService {

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final TurnoRepository turnos;

    public AgendaConsultaService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            TurnoRepository turnos) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.turnos = turnos;
    }

    /** Turnos que empiezan entre {@code desde} y {@code hasta} (ambos días completos), todos los estados. */
    @Transactional(readOnly = true)
    public List<TurnoDto> agenda(LocalDate desde, LocalDate hasta) {
        RangoConsulta.validar(desde, hasta);
        Long consultorioId = consultorioActual.id();
        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        ZoneId zona = ZoneId.of(consultorio.getZonaHoraria());
        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        return turnos.listarPorInicioEntre(consultorioId, inicio, fin).stream().map(TurnoDto::de).toList();
    }
}
