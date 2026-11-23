package ar.edu.odontogestion.application.listaespera;

import ar.edu.odontogestion.application.acciones.ResumenAccionResponse;
import ar.edu.odontogestion.application.comun.RestriccionesDb;
import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.application.paciente.PacienteService;
import ar.edu.odontogestion.application.paciente.ResultadoBuscarOCrear;
import ar.edu.odontogestion.application.token.TokenAccesoService;
import ar.edu.odontogestion.domain.agenda.Prestacion;
import ar.edu.odontogestion.domain.agenda.PrestacionRepository;
import ar.edu.odontogestion.domain.consultorio.Consultorio;
import ar.edu.odontogestion.domain.consultorio.ConsultorioRepository;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.listaespera.EntradaDuplicadaException;
import ar.edu.odontogestion.domain.listaespera.EntradaListaEspera;
import ar.edu.odontogestion.domain.listaespera.EntradaListaEsperaRepository;
import ar.edu.odontogestion.domain.listaespera.EstadoEntrada;
import ar.edu.odontogestion.domain.listaespera.OrigenEntrada;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.paciente.PacienteRepository;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la lista de espera (C-10): inscripción pública, confirmación por enlace, listado y retiro del
 * odontólogo. Que un paciente tenga una sola entrada vigente por prestación lo decide la base
 * ({@code uq_entrada_lista_espera_vigente}); acá solo se traduce la violación. Nunca loguea DNI, email ni nombres.
 */
@Service
public class ListaEsperaService {

    /** Plazo fijo para confirmar la inscripción por el enlace del email (C-10 D2). */
    static final Duration PLAZO_CONFIRMACION = Duration.ofHours(24);
    static final String RESTRICCION_VIGENTE = "uq_entrada_lista_espera_vigente";

    private final ConsultorioActual consultorioActual;
    private final ConsultorioRepository consultorios;
    private final PrestacionRepository prestaciones;
    private final PacienteService pacientes;
    private final PacienteRepository pacienteRepository;
    private final EntradaListaEsperaRepository entradas;
    private final NotificationService notificaciones;
    private final TokenAccesoService tokens;
    private final AppProperties appProperties;
    private final Clock clock;

    public ListaEsperaService(ConsultorioActual consultorioActual, ConsultorioRepository consultorios,
            PrestacionRepository prestaciones, PacienteService pacientes, PacienteRepository pacienteRepository,
            EntradaListaEsperaRepository entradas, NotificationService notificaciones, TokenAccesoService tokens,
            AppProperties appProperties, Clock clock) {
        this.consultorioActual = consultorioActual;
        this.consultorios = consultorios;
        this.prestaciones = prestaciones;
        this.pacientes = pacientes;
        this.pacienteRepository = pacienteRepository;
        this.entradas = entradas;
        this.notificaciones = notificaciones;
        this.tokens = tokens;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /**
     * Inscribe al paciente (lo crea si no existe, sin tocar a uno existente) con la entrada
     * {@code PENDIENTE_CONFIRMACION} y encola el email de confirmación, todo en una transacción.
     *
     * @throws ValidacionDominioException rango inválido, {@code fechaHasta} anterior a hoy o prestación inactiva (422)
     * @throws RecursoNoEncontradoException prestación inexistente (404)
     * @throws EntradaDuplicadaException ya hay una entrada vigente del paciente para la prestación (409)
     */
    @Transactional
    public ResultadoInscripcion inscribir(DatosInscripcion datos) {
        Long consultorioId = consultorioActual.id();
        Instant ahora = clock.instant();
        validarRango(datos.fechaDesde(), datos.fechaHasta(), ahora);
        Prestacion prestacion = prestaciones.findByIdAndConsultorio_Id(datos.prestacionId(), consultorioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestación no encontrada"));
        if (!prestacion.isActiva()) {
            throw new ValidacionDominioException("prestacionId", "La prestación está desactivada.");
        }

        ResultadoBuscarOCrear resultado = pacientes.buscarOCrearPorDni(datos.paciente());
        if (!resultado.paciente().activo()) {
            return ResultadoInscripcion.PACIENTE_DE_BAJA;
        }
        Long pacienteId = resultado.paciente().id();
        // Una pendiente vencida no cuenta: se libera el único parcial antes de insertar.
        entradas.vencerPendientesVencidas(consultorioId, pacienteId, prestacion.getId(), ahora);

        Consultorio consultorio = consultorios.findById(consultorioId).orElseThrow();
        Instant pendienteHasta = ahora.plus(PLAZO_CONFIRMACION);
        EntradaListaEspera entrada = EntradaListaEspera.pendiente(consultorio,
                pacienteRepository.getReferenceById(pacienteId), prestacion, datos.fechaDesde(), datos.fechaHasta(),
                OrigenEntrada.PUBLICA, pendienteHasta);
        try {
            entrada = entradas.saveAndFlush(entrada);
        } catch (DataIntegrityViolationException e) {
            if (RestriccionesDb.viola(e, RESTRICCION_VIGENTE)) {
                throw new EntradaDuplicadaException();
            }
            throw e;
        }
        notificaciones.encolar(TipoNotificacion.CONFIRMAR_LISTA_ESPERA, resultado.paciente().email(),
                new ReferenciaToken.EntradaListaEspera(entrada.getId()),
                Map.of("consultorio", consultorio.getNombre(), "prestacion", prestacion.getNombre()),
                List.of(new EnlaceProgramado(PropositoToken.CONFIRMAR_LISTA_ESPERA, pendienteHasta)));
        return ResultadoInscripcion.CREADA;
    }

    /**
     * Qué muestra el enlace de confirmación, sin consumirlo ni modificar nada.
     *
     * @throws TokenInvalidoException la entrada no existe, ya no está pendiente o su plazo venció (410)
     */
    @Transactional(readOnly = true)
    public ResumenAccionResponse resumenConfirmacion(ReferenciaToken.EntradaListaEspera ref, Instant venceEn) {
        EntradaListaEspera entrada = cargarConfirmable(ref);
        return new ResumenAccionResponse(PropositoToken.CONFIRMAR_LISTA_ESPERA, venceEn, true, null, null, null,
                resumen(entrada));
    }

    /**
     * Activa la entrada. Se llama dentro de la transacción de {@code AccionTokenService.ejecutar}, que ya consumió el
     * token: si la entrada no se puede confirmar se lanza {@link TokenInvalidoException} y el consumo se revierte.
     */
    @Transactional
    public ResumenAccionResponse confirmar(ReferenciaToken.EntradaListaEspera ref) {
        EntradaListaEspera entrada = cargarConfirmable(ref);
        entrada.confirmar();
        entradas.saveAndFlush(entrada);
        return new ResumenAccionResponse(PropositoToken.CONFIRMAR_LISTA_ESPERA, null, false,
                ResumenAccionResponse.Motivo.ESTADO_NO_PERMITE, null, null, resumen(entrada));
    }

    /** Todas las entradas del consultorio, de la más reciente a la más antigua (C-10 D7). */
    @Transactional(readOnly = true)
    public List<EntradaListaEsperaVista> listar() {
        return entradas.listar(consultorioActual.id()).stream().map(EntradaListaEsperaVista::de).toList();
    }

    /**
     * Retira una entrada {@code PENDIENTE_CONFIRMACION} o {@code ACTIVA} y revoca su enlace de confirmación.
     *
     * @throws RecursoNoEncontradoException no existe en el consultorio (404)
     * @throws ar.edu.odontogestion.domain.listaespera.TransicionListaEsperaInvalidaException ya está en estado final (422)
     */
    @Transactional
    public void cancelar(Long id) {
        EntradaListaEspera entrada = entradas.findByIdAndConsultorio_Id(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Entrada no encontrada"));
        entrada.cancelar();
        entradas.saveAndFlush(entrada);
        tokens.revocar(new ReferenciaToken.EntradaListaEspera(id), Optional.of(PropositoToken.CONFIRMAR_LISTA_ESPERA));
    }

    private EntradaListaEspera cargarConfirmable(ReferenciaToken.EntradaListaEspera ref) {
        EntradaListaEspera entrada = entradas.findByIdAndConsultorio_Id(ref.id(), consultorioActual.id())
                .orElseThrow(TokenInvalidoException::new);
        if (entrada.getEstado() != EstadoEntrada.PENDIENTE_CONFIRMACION || entrada.pendienteVencida(clock.instant())) {
            throw new TokenInvalidoException();
        }
        return entrada;
    }

    private static ResumenAccionResponse.EntradaResumen resumen(EntradaListaEspera e) {
        return new ResumenAccionResponse.EntradaResumen(e.getConsultorio().getNombre(), e.getPrestacion().getNombre(),
                e.getFechaDesde(), e.getFechaHasta(), e.getEstado());
    }

    private void validarRango(LocalDate desde, LocalDate hasta, Instant ahora) {
        if (hasta.isBefore(desde)) {
            throw new ValidacionDominioException("fechaHasta", "No puede ser anterior a la fecha desde.");
        }
        LocalDate hoy = ahora.atZone(appProperties.timezone()).toLocalDate();
        if (hasta.isBefore(hoy)) {
            throw new ValidacionDominioException("fechaHasta", "No puede ser anterior a hoy.");
        }
    }
}
