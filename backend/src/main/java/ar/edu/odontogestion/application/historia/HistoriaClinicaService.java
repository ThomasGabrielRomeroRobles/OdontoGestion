package ar.edu.odontogestion.application.historia;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.historia.Anamnesis;
import ar.edu.odontogestion.domain.historia.AnamnesisRepository;
import ar.edu.odontogestion.domain.historia.DatosAnamnesis;
import ar.edu.odontogestion.domain.historia.EstadoPieza;
import ar.edu.odontogestion.domain.historia.EvolucionClinica;
import ar.edu.odontogestion.domain.historia.EvolucionClinicaRepository;
import ar.edu.odontogestion.domain.historia.FechaEvolucionFuturaException;
import ar.edu.odontogestion.domain.historia.HistoriaClinica;
import ar.edu.odontogestion.domain.historia.HistoriaClinicaRepository;
import ar.edu.odontogestion.domain.historia.PiezaDentalEstado;
import ar.edu.odontogestion.domain.historia.PiezaDentalEstadoRepository;
import ar.edu.odontogestion.domain.historia.PiezaFdi;
import ar.edu.odontogestion.domain.historia.TurnoDeOtroPacienteException;
import ar.edu.odontogestion.domain.paciente.Paciente;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import ar.edu.odontogestion.domain.usuario.Usuario;
import ar.edu.odontogestion.domain.usuario.UsuarioRepository;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.infrastructure.config.HistoriaProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Historia clínica del paciente (C-05): anamnesis, odontograma FDI y evoluciones. Solo la usa el odontólogo con
 * sesión (RN-HC-02, lo garantiza la cadena de seguridad). La historia se crea en la primera escritura (design D2) y
 * la consulta nunca escribe. Este servicio no loguea: ningún dato clínico puede llegar a los logs (RN-HC-06), y los
 * mensajes de error son textos fijos.
 */
@Service
public class HistoriaClinicaService {

    static final String MENSAJE_PACIENTE_NO_ENCONTRADO = "Paciente no encontrado.";
    static final String MENSAJE_EVOLUCION_NO_ENCONTRADA = "Evolución no encontrada.";

    private final ConsultorioActual consultorioActual;
    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final TurnoRepository turnos;
    private final HistoriaClinicaRepository historias;
    private final AnamnesisRepository anamnesis;
    private final PiezaDentalEstadoRepository piezas;
    private final EvolucionClinicaRepository evoluciones;
    private final AppProperties appProperties;
    private final HistoriaProperties historiaProperties;
    private final Clock clock;

    public HistoriaClinicaService(ConsultorioActual consultorioActual, PacienteRepository pacientes,
            UsuarioRepository usuarios, TurnoRepository turnos, HistoriaClinicaRepository historias,
            AnamnesisRepository anamnesis, PiezaDentalEstadoRepository piezas, EvolucionClinicaRepository evoluciones,
            AppProperties appProperties, HistoriaProperties historiaProperties, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.pacientes = pacientes;
        this.usuarios = usuarios;
        this.turnos = turnos;
        this.historias = historias;
        this.anamnesis = anamnesis;
        this.piezas = piezas;
        this.evoluciones = evoluciones;
        this.appProperties = appProperties;
        this.historiaProperties = historiaProperties;
        this.clock = clock;
    }

    /** Ficha completa; para un paciente sin historia devuelve una ficha vacía sin crearla. */
    @Transactional(readOnly = true)
    public HistoriaVista consultar(Long pacienteId, String emailUsuario) {
        Paciente paciente = cargarPaciente(pacienteId);
        PacienteResumen resumen = new PacienteResumen(paciente.getId(), paciente.getNombre(), paciente.getApellido());
        Optional<HistoriaClinica> historia = historias.findByPaciente_Id(paciente.getId());
        if (historia.isEmpty()) {
            List<PiezaVista> vacias = PiezaFdi.PERMANENTES.stream()
                    .map(p -> PiezaVista.sinRegistrar(p.numero())).toList();
            return new HistoriaVista(resumen, AnamnesisVista.VACIA, vacias, List.of());
        }
        Long historiaId = historia.get().getId();
        AnamnesisVista vistaAnamnesis = anamnesis.findByHistoria_Id(historiaId)
                .map(AnamnesisVista::desde).orElse(AnamnesisVista.VACIA);
        return new HistoriaVista(resumen, vistaAnamnesis, odontograma(historiaId),
                evolucionesDe(historiaId, cargarUsuario(emailUsuario)));
    }

    @Transactional
    public AnamnesisVista guardarAnamnesis(Long pacienteId, DatosAnamnesis datos, String emailUsuario) {
        Paciente paciente = cargarPaciente(pacienteId);
        Usuario autor = cargarUsuario(emailUsuario);
        HistoriaClinica historia = obtenerOCrear(paciente);
        Anamnesis actual = anamnesis.findByHistoria_Id(historia.getId())
                .map(a -> {
                    a.reemplazar(datos, autor);
                    return a;
                })
                .orElseGet(() -> Anamnesis.nueva(historia, datos, autor));
        return AnamnesisVista.desde(anamnesis.saveAndFlush(actual));
    }

    /** Valida la pieza antes de tocar la base: una pieza inválida no crea la historia (design D3). */
    @Transactional
    public PiezaVista registrarPieza(Long pacienteId, int numeroFdi, EstadoPieza estado, String observacion,
            String emailUsuario) {
        PiezaFdi pieza = PiezaFdi.of(numeroFdi);
        Paciente paciente = cargarPaciente(pacienteId);
        Usuario autor = cargarUsuario(emailUsuario);
        HistoriaClinica historia = obtenerOCrear(paciente);
        PiezaDentalEstado actual = piezas.findByHistoria_IdAndPiezaFdi(historia.getId(), (short) pieza.numero())
                .map(p -> {
                    p.actualizar(estado, observacion, autor);
                    return p;
                })
                .orElseGet(() -> PiezaDentalEstado.nueva(historia, pieza, estado, observacion, autor));
        return PiezaVista.desde(piezas.saveAndFlush(actual));
    }

    @Transactional(readOnly = true)
    public List<EvolucionVista> listarEvoluciones(Long pacienteId, String emailUsuario) {
        Paciente paciente = cargarPaciente(pacienteId);
        return historias.findByPaciente_Id(paciente.getId())
                .map(h -> evolucionesDe(h.getId(), cargarUsuario(emailUsuario)))
                .orElse(List.of());
    }

    @Transactional
    public EvolucionVista registrarEvolucion(Long pacienteId, DatosEvolucion datos, String emailUsuario) {
        Paciente paciente = cargarPaciente(pacienteId);
        Usuario autor = cargarUsuario(emailUsuario);
        LocalDate fecha = validarFecha(datos.fecha() == null ? hoy() : datos.fecha());
        Turno turno = datos.turnoId() == null ? null : turnoDelPaciente(datos.turnoId(), paciente);
        HistoriaClinica historia = obtenerOCrear(paciente);
        EvolucionClinica nueva = EvolucionClinica.nueva(historia, autor, turno, fecha, datos.nota());
        return vista(evoluciones.saveAndFlush(nueva), autor);
    }

    @Transactional
    public EvolucionVista editarEvolucion(Long pacienteId, Long evolucionId, DatosEvolucion datos,
            String emailUsuario) {
        Paciente paciente = cargarPaciente(pacienteId);
        Usuario usuario = cargarUsuario(emailUsuario);
        EvolucionClinica evolucion = historias.findByPaciente_Id(paciente.getId())
                .flatMap(h -> evoluciones.findByIdAndHistoria_Id(evolucionId, h.getId()))
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_EVOLUCION_NO_ENCONTRADA));
        LocalDate fecha = datos.fecha() == null ? evolucion.getFecha() : validarFecha(datos.fecha());
        evolucion.editar(usuario.getId(), fecha, datos.nota(), clock.instant(),
                historiaProperties.ventanaEdicionEvolucion());
        return vista(evoluciones.saveAndFlush(evolucion), usuario);
    }

    /** INSERT idempotente + lectura: dos primeras escrituras concurrentes usan la misma historia (design D2). */
    private HistoriaClinica obtenerOCrear(Paciente paciente) {
        historias.insertarSiNoExiste(consultorioActual.id(), paciente.getId(), clock.instant());
        return historias.findByPaciente_Id(paciente.getId())
                .orElseThrow(() -> new IllegalStateException("Historia no visible tras crearla"));
    }

    private List<PiezaVista> odontograma(Long historiaId) {
        Map<Integer, PiezaDentalEstado> registradas = piezas.findByHistoria_Id(historiaId).stream()
                .collect(Collectors.toMap(PiezaDentalEstado::getPiezaFdi, Function.identity()));
        return PiezaFdi.PERMANENTES.stream()
                .map(p -> Optional.ofNullable(registradas.get(p.numero())).map(PiezaVista::desde)
                        .orElseGet(() -> PiezaVista.sinRegistrar(p.numero())))
                .toList();
    }

    private List<EvolucionVista> evolucionesDe(Long historiaId, Usuario usuario) {
        return evoluciones.findByHistoria_IdOrderByFechaAscCreadoEnAscIdAsc(historiaId).stream()
                .map(e -> vista(e, usuario)).toList();
    }

    private EvolucionVista vista(EvolucionClinica e, Usuario usuario) {
        Instant ahora = clock.instant();
        Long turnoId = e.getTurno() == null ? null : e.getTurno().getId();
        return new EvolucionVista(e.getId(), e.getFecha(), e.getNota(), turnoId, AutorVista.desde(e.getAutor()),
                e.getCreadoEn(), e.getEditadaEn(),
                e.esEditablePor(usuario.getId(), ahora, historiaProperties.ventanaEdicionEvolucion()));
    }

    private Turno turnoDelPaciente(Long turnoId, Paciente paciente) {
        return turnos.findByIdAndConsultorio_Id(turnoId, consultorioActual.id())
                .filter(t -> t.getPaciente().getId().equals(paciente.getId()))
                .orElseThrow(TurnoDeOtroPacienteException::new);
    }

    private LocalDate validarFecha(LocalDate fecha) {
        if (fecha.isAfter(hoy())) {
            throw new FechaEvolucionFuturaException();
        }
        return fecha;
    }

    private LocalDate hoy() {
        return LocalDate.now(clock.withZone(appProperties.timezone()));
    }

    private Paciente cargarPaciente(Long id) {
        return pacientes.findByIdAndConsultorioId(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_PACIENTE_NO_ENCONTRADO));
    }

    private Usuario cargarUsuario(String email) {
        return usuarios.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuario de la sesión inexistente"));
    }
}
