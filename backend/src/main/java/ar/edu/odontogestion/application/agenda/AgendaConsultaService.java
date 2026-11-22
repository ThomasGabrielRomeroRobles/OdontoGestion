package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.turno.TurnoDto;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
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
    private final Clock clock;

    public AgendaConsultaService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            TurnoRepository turnos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.turnos = turnos;
        this.clock = clock;
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

    /**
     * Turnos del día actual en la zona del consultorio, sin {@code CANCELADO} ni {@code EXPIRADO}, ordenados por
     * inicio. El límite del día es el local (un turno de las 22:00 en Buenos Aires es 01:00Z del día siguiente).
     */
    @Transactional(readOnly = true)
    public List<TurnoDto> hoy() {
        Long consultorioId = consultorioActual.id();
        ZoneId zona = ZoneId.of(consultorios.findById(consultorioId).orElseThrow().getZonaHoraria());
        LocalDate hoy = clock.instant().atZone(zona).toLocalDate();
        return turnos.listarPorInicioEntre(consultorioId, hoy.atStartOfDay(zona).toInstant(),
                hoy.plusDays(1).atStartOfDay(zona).toInstant()).stream()
                .filter(t -> t.getEstado().ocupaAgenda()).map(TurnoDto::de).toList();
    }
}
