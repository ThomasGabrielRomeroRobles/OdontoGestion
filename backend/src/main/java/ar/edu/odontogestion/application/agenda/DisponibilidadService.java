package ar.edu.odontogestion.application.agenda;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.agenda.CalculadoraDisponibilidad;
import ar.edu.odontogestion.domain.agenda.FranjaHoraria;
import ar.edu.odontogestion.domain.agenda.Hueco;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.agenda.PoliticaAnticipacion;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidad orientativa (design D7): arma la entrada de {@link CalculadoraDisponibilidad} desde los
 * repositorios. No garantiza el hueco: la garantía es la restricción de la base (regla dura #8).
 */
@Service
public class DisponibilidadService {

    /** Ventana de búsqueda de alternativas ante un conflicto (D6). */
    static final int VENTANA_ALTERNATIVAS_DIAS = 7;

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final PrestacionRepository prestaciones;
    private final HorarioTrabajoRepository horarios;
    private final BloqueoAgendaRepository bloqueos;
    private final TurnoRepository turnos;
    private final Clock clock;

    public DisponibilidadService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            PrestacionRepository prestaciones, HorarioTrabajoRepository horarios, BloqueoAgendaRepository bloqueos,
            TurnoRepository turnos, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.prestaciones = prestaciones;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.clock = clock;
    }

    /**
     * Huecos libres para una prestación activa entre dos fechas locales inclusivas (API del odontólogo: política
     * {@link PoliticaAnticipacion#odontologo()}, sin anticipación mínima).
     */
    @Transactional(readOnly = true)
    public List<Hueco> huecosLibres(Long prestacionId, LocalDate desde, LocalDate hasta) {
        RangoConsulta.validar(desde, hasta);
        Long consultorioId = consultorioActual.id();
        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        Prestacion prestacion = prestaciones.findByIdAndConsultorio_Id(prestacionId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestación no encontrada"));
        if (!prestacion.isActiva()) {
            throw new ValidacionDominioException("prestacionId", "La prestación está desactivada.");
        }
        return calcular(consultorio, prestacion, desde, hasta,
                PoliticaAnticipacion.odontologo().minimoInicio(clock.instant()));
    }

    /**
     * Hasta {@code maximo} huecos libres para la misma prestación a partir de {@code desde}, en los próximos
     * {@value #VENTANA_ALTERNATIVAS_DIAS} días y con la política del odontólogo. Corre en una transacción de solo
     * lectura propia: se llama después de que la transacción de escritura falló (D6).
     */
    @Transactional(readOnly = true)
    public List<Hueco> alternativas(Long prestacionId, Instant desde, int maximo) {
        Long consultorioId = consultorioActual.id();
        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        Prestacion prestacion = prestaciones.findByIdAndConsultorio_Id(prestacionId, consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestación no encontrada"));
        LocalDate primerDia = desde.atZone(ZoneId.of(consultorio.getZonaHoraria())).toLocalDate();
        Instant ahora = clock.instant();
        Instant minimo = ahora.isAfter(desde) ? ahora : desde;
        return calcular(consultorio, prestacion, primerDia, primerDia.plusDays(VENTANA_ALTERNATIVAS_DIAS - 1), minimo)
                .stream().limit(maximo).toList();
    }

    private List<Hueco> calcular(Consultorio consultorio, Prestacion prestacion, LocalDate desde, LocalDate hasta,
            Instant minimoInicio) {
        Long consultorioId = consultorio.getId();
        ZoneId zona = ZoneId.of(consultorio.getZonaHoraria());
        Instant ventanaInicio = desde.atStartOfDay(zona).toInstant();
        Instant ventanaFin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        List<FranjaHoraria> franjas = horarios.findByConsultorio_IdOrderByDiaSemanaAscHoraDesdeAsc(consultorioId)
                .stream().map(h -> new FranjaHoraria(h.getDiaSemana(), h.getHoraDesde(), h.getHoraHasta())).toList();
        List<Hueco> bloqueados = bloqueos
                .findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(consultorioId, ventanaInicio,
                        ventanaFin)
                .stream().map(b -> new Hueco(b.getDesde(), b.getHasta())).toList();
        List<Hueco> ocupados = turnos.intervalosOcupados(consultorioId, ventanaInicio, ventanaFin, clock.instant());

        return CalculadoraDisponibilidad.calcular(desde, hasta, zona, franjas, bloqueados, ocupados,
                prestacion.getDuracionMin(), consultorio.getParametros().granularidadMin(), minimoInicio);
    }
}
