package ar.edu.odontogestion.application.demo;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.domain.agenda.BloqueoAgendaRepository;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajo;
import ar.edu.odontogestion.domain.agenda.HorarioTrabajoRepository;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.consultorio.ParametrosConsultorio;
import ar.edu.odontogestion.domain.paciente.DatosPaciente;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.EstadoTurno;
import ar.edu.odontogestion.domain.turno.OrigenTurno;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos ficticios de la demo (C-12 design D2/D3, spec {@code demo-environment}): 10 pacientes y unos 17 turnos de la
 * semana en curso, calculada con el {@link Clock} en la zona del consultorio. Solo siembra si el consultorio no tiene
 * pacientes, así un segundo arranque no duplica nada.
 *
 * <p>Los turnos se crean con las fábricas del dominio y {@link TurnoRepository#save}, nunca con los comandos de
 * turnos: así no se publican eventos ni se encolan emails a las direcciones ficticias. El estado de cada turno sale
 * de su hora respecto de "ahora":
 * <ul>
 *   <li>ya pasó la tolerancia: {@code ATENDIDO}, o {@code AUSENTE} si está marcado como ausencia (y se suma al
 *   contador del paciente);</li>
 *   <li>futuro dentro de las horas del recordatorio: {@code RESERVADO} o {@code CONFIRMADO} según el plan. Un
 *   {@code RESERVADO} creado con menos de esas horas de anticipación nunca recibe recordatorio (RN-NO-09, C-11);</li>
 *   <li>más lejano: siempre {@code CONFIRMADO}, que tampoco recibe recordatorio (C-11 D2). Así la demo no dispara
 *   recordatorios a {@code @demo.example} aunque quede corriendo varios días (C-12 grupo 5.2).</li>
 * </ul>
 * Un turno que cae fuera del horario, con una prestación inactiva o sobre un bloqueo u otro turno se omite con un
 * {@code WARN} sin datos personales. La no superposición la sigue garantizando la base (regla dura #8).
 */
@Service
public class DemoSeedService {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedService.class);

    /** Nombres inventados (regla dura #12). DNI {@code 90000001}–{@code 90000010}, emails {@code @demo.example}. */
    private static final List<String[]> PACIENTES = List.of(
            new String[] {"Lucía", "Ferreyra"}, new String[] {"Martín", "Quiroga"},
            new String[] {"Sofía", "Benítez"}, new String[] {"Tomás", "Aguirre"},
            new String[] {"Valentina", "Ledesma"}, new String[] {"Joaquín", "Paredes"},
            new String[] {"Camila", "Sosa"}, new String[] {"Mateo", "Villalba"},
            new String[] {"Julieta", "Ojeda"}, new String[] {"Bruno", "Cabrera"});

    /** Plan de la semana: día, hora local, prestación (de V4), paciente, ausencia si ya pasó, confirmado si es futuro. */
    private record Franja(DayOfWeek dia, LocalTime hora, String prestacion, int paciente, boolean ausencia,
            boolean confirmado) {

        static Franja de(DayOfWeek dia, String hora, String prestacion, int paciente) {
            return new Franja(dia, LocalTime.parse(hora), prestacion, paciente, false, false);
        }

        Franja comoAusencia() {
            return new Franja(dia, hora, prestacion, paciente, true, confirmado);
        }

        Franja comoConfirmado() {
            return new Franja(dia, hora, prestacion, paciente, ausencia, true);
        }
    }

    private static final List<Franja> PLAN = List.of(
            Franja.de(DayOfWeek.MONDAY, "09:00", "Consulta", 2),
            Franja.de(DayOfWeek.MONDAY, "10:30", "Limpieza", 0).comoAusencia(),
            Franja.de(DayOfWeek.MONDAY, "14:30", "Obturación", 3),
            Franja.de(DayOfWeek.TUESDAY, "09:00", "Control", 4),
            Franja.de(DayOfWeek.TUESDAY, "10:30", "Consulta", 1).comoAusencia(),
            Franja.de(DayOfWeek.TUESDAY, "16:00", "Limpieza", 0).comoAusencia(),
            Franja.de(DayOfWeek.WEDNESDAY, "09:00", "Consulta", 5),
            Franja.de(DayOfWeek.WEDNESDAY, "10:30", "Control", 6),
            Franja.de(DayOfWeek.WEDNESDAY, "14:30", "Limpieza", 7).comoConfirmado(),
            Franja.de(DayOfWeek.WEDNESDAY, "16:00", "Consulta", 8),
            Franja.de(DayOfWeek.THURSDAY, "09:00", "Obturación", 9),
            Franja.de(DayOfWeek.THURSDAY, "10:30", "Consulta", 2).comoConfirmado(),
            Franja.de(DayOfWeek.THURSDAY, "14:30", "Extracción", 3),
            Franja.de(DayOfWeek.FRIDAY, "09:00", "Endodoncia", 4),
            Franja.de(DayOfWeek.FRIDAY, "11:00", "Consulta", 5),
            Franja.de(DayOfWeek.FRIDAY, "14:30", "Control", 6),
            Franja.de(DayOfWeek.FRIDAY, "16:00", "Limpieza", 1));

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final ConsultorioService consultorioService;
    private final PacienteRepository pacientes;
    private final PrestacionRepository prestaciones;
    private final HorarioTrabajoRepository horarios;
    private final BloqueoAgendaRepository bloqueos;
    private final TurnoRepository turnos;
    private final AppProperties appProperties;
    private final Clock clock;

    public DemoSeedService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            ConsultorioService consultorioService, PacienteRepository pacientes, PrestacionRepository prestaciones,
            HorarioTrabajoRepository horarios, BloqueoAgendaRepository bloqueos, TurnoRepository turnos,
            AppProperties appProperties, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.consultorioService = consultorioService;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /** Siembra pacientes y turnos ficticios si el consultorio actual no tiene pacientes; si no, no hace nada. */
    @Transactional
    public ResultadoSeedDemo sembrar() {
        Long consultorioId = consultorioActual.id();
        if (pacientes.existsByConsultorioId(consultorioId)) {
            log.info("Seed de demo: el consultorio ya tiene pacientes, no se siembra nada");
            return ResultadoSeedDemo.NADA;
        }
        Consultorio consultorio = consultorios.getReferenceById(consultorioId);
        List<Paciente> creados = new ArrayList<>();
        for (int i = 0; i < PACIENTES.size(); i++) {
            String[] nombre = PACIENTES.get(i);
            int n = i + 1;
            creados.add(pacientes.save(Paciente.nuevo(consultorio, new DatosPaciente(nombre[0], nombre[1],
                    String.valueOf(90_000_000 + n), null, String.format("+54 11 5555-%04d", n),
                    String.format("paciente%02d@demo.example", n), null))));
        }

        ParametrosConsultorio parametros = consultorioService.parametrosVigentes();
        Instant ahora = clock.instant();
        ZoneId zona = appProperties.timezone();
        LocalDate lunes = LocalDate.ofInstant(ahora, zona).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, Prestacion> activas = prestaciones.findByConsultorio_IdAndActivaTrueOrderByNombreAsc(consultorioId)
                .stream().collect(Collectors.toMap(p -> p.getNombre().toLowerCase(), Function.identity()));
        List<HorarioTrabajo> semana = horarios.findByConsultorio_IdOrderByDiaSemanaAscHoraDesdeAsc(consultorioId);

        int sembrados = 0;
        int omitidos = 0;
        List<Long> ausencias = new ArrayList<>();
        for (Franja franja : PLAN) {
            Prestacion prestacion = activas.get(franja.prestacion().toLowerCase());
            Instant inicio = lunes.with(TemporalAdjusters.nextOrSame(franja.dia())).atTime(franja.hora())
                    .atZone(zona).toInstant();
            if (prestacion == null || !libre(consultorioId, semana, franja, prestacion, inicio, ahora)) {
                log.warn("Seed de demo: se omite un turno ({} {}): la franja no está disponible", franja.dia(),
                        franja.hora());
                omitidos++;
                continue;
            }
            Paciente paciente = creados.get(franja.paciente());
            Turno turno = Turno.reservado(consultorio, paciente, prestacion, inicio, OrigenTurno.ODONTOLOGO);
            aplicarEstado(turno, franja, ahora, parametros);
            turnos.save(turno);
            if (turno.getEstado() == EstadoTurno.AUSENTE) {
                ausencias.add(paciente.getId());
            }
            sembrados++;
        }
        // Al final: incrementarAusencias limpia el contexto de persistencia (RN-AS-02, mismo contador que C-08).
        ausencias.forEach(pacientes::incrementarAusencias);
        log.info("Seed de demo: {} pacientes y {} turnos ficticios ({} omitidos)", creados.size(), sembrados,
                omitidos);
        return new ResultadoSeedDemo(creados.size(), sembrados, omitidos);
    }

    private void aplicarEstado(Turno turno, Franja franja, Instant ahora, ParametrosConsultorio parametros) {
        int tolerancia = parametros.toleranciaAusenteMin();
        Instant finTolerancia = turno.getInicio().plus(Duration.ofMinutes(tolerancia));
        if (!ahora.isBefore(finTolerancia)) {
            if (franja.ausencia()) {
                turno.marcarAusente(finTolerancia, tolerancia);
            } else {
                turno.marcarAtendido();
            }
            return;
        }
        Instant ventanaRecordatorio = ahora.plus(Duration.ofHours(parametros.recordatorioHorasAntes()));
        if (franja.confirmado() || !turno.getInicio().isBefore(ventanaRecordatorio)) {
            turno.confirmar();
        }
    }

    /** Dentro de un horario de trabajo y sin bloqueos ni turnos que ocupen la franja (chequeo orientativo). */
    private boolean libre(Long consultorioId, List<HorarioTrabajo> semana, Franja franja, Prestacion prestacion,
            Instant inicio, Instant ahora) {
        Instant fin = inicio.plus(Duration.ofMinutes(prestacion.getDuracionMin()));
        LocalTime horaFin = franja.hora().plusMinutes(prestacion.getDuracionMin());
        boolean enHorario = semana.stream().anyMatch(h -> h.getDiaSemana() == franja.dia().getValue()
                && !franja.hora().isBefore(h.getHoraDesde()) && !horaFin.isAfter(h.getHoraHasta())
                && horaFin.isAfter(franja.hora()));
        return enHorario
                && bloqueos.findByConsultorio_IdAndHastaGreaterThanAndDesdeLessThanOrderByDesdeAsc(consultorioId,
                        inicio, fin).isEmpty()
                && turnos.ocupantesQueSolapan(consultorioId, inicio, fin, ahora).isEmpty();
    }
}
