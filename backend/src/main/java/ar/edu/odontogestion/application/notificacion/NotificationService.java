package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.ConflictoException;
import ar.edu.odontogestion.domain.error.RecursoNoEncontradoException;
import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.EstadoNotificacion;
import ar.edu.odontogestion.domain.notificacion.Notificacion;
import ar.edu.odontogestion.domain.notificacion.NotificacionRepository;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.infrastructure.config.AppProperties;
import ar.edu.odontogestion.infrastructure.config.NotificationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox de notificaciones (C-03, design D6–D8, D10).
 *
 * <p>Contrato para C-07/C-09/C-10/C-11: llamar a {@link #encolar} <b>dentro de la transacción de la operación de
 * negocio</b>; la notificación se confirma o se descarta junto con ella y nunca se envía en ese momento. El envío
 * lo hace el procesamiento periódico ({@link #procesarPendientes()}).
 *
 * <p>Seguridad: los enlaces con token existen solo en memoria durante el envío; ni la fila, ni el error, ni los
 * logs los contienen (RN-TK-02). Tampoco se loguean destinatarios ni cuerpos.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final String ENLACE_VENCIDO = "ENLACE_VENCIDO";
    /** Tope de reintentos manuales por notificación (design D13). */
    static final int MAX_REINTENTOS_MANUALES = 3;

    private final NotificacionRepository repository;
    private final ConsultorioActual consultorioActual;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NotificationChannel canal;
    private final EmisorEnlaces emisorEnlaces;
    private final AppProperties appProperties;
    private final PoliticaReintento politica;
    private final int lote;
    private final TransactionTemplate txPorNotificacion;

    /**
     * @param canal canal de envío; el MVP tiene solo {@code EMAIL} (RN-NO-01), con varios canales se elegiría por
     *              {@code Notificacion.getCanal()}
     */
    public NotificationService(NotificacionRepository repository, ConsultorioActual consultorioActual,
            ObjectMapper objectMapper, Clock clock, NotificationChannel canal, EmisorEnlaces emisorEnlaces,
            AppProperties appProperties, NotificationProperties notificationProperties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.consultorioActual = consultorioActual;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.canal = canal;
        this.emisorEnlaces = emisorEnlaces;
        this.appProperties = appProperties;
        this.politica = new PoliticaReintento(notificationProperties.maxIntentos(), notificationProperties.esperas());
        this.lote = notificationProperties.lote();
        this.txPorNotificacion = new TransactionTemplate(transactionManager);
        this.txPorNotificacion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Encola una notificación {@code PENDIENTE} programada para ahora, de forma idempotente (RN-NO-03): un
     * duplicado (mismo turno y tipo, mismo aviso, misma entrada y tipo) se ignora sin afectar la transacción.
     *
     * @param datos variables de la plantilla del tipo; variables no declaradas se rechazan (RN-NO-05)
     * @param enlaces propósito y vencimiento de cada enlace; el token se emite recién al enviar (RN-TK-02)
     * @return {@code true} si se insertó, {@code false} si ya existía
     * @throws ValidacionDominioException si los datos, los enlaces, el destinatario o la referencia no son válidos
     */
    @Transactional
    public boolean encolar(TipoNotificacion tipo, String destinatario, ReferenciaToken referencia,
            Map<String, String> datos, List<EnlaceProgramado> enlaces) {
        PlantillaNotificacion plantilla = PlantillaNotificacion.de(tipo);
        plantilla.validarDatos(datos);
        plantilla.validarEnlaces(enlaces.stream().map(EnlaceProgramado::proposito).toList());
        if (destinatario == null || !EMAIL.matcher(destinatario.strip()).matches()) {
            throw new ValidacionDominioException("destinatario", "Debe ser un email válido.");
        }
        if (!tipo.admite(referencia)) {
            throw new ValidacionDominioException("referencia",
                    "El tipo " + tipo + " requiere una referencia " + tipo.referenciaRequerida().getSimpleName() + ".");
        }

        Instant ahora = clock.instant();
        int insertadas = repository.insertarSiNoExiste(consultorioActual.id(), tipo.name(),
                CanalNotificacion.EMAIL.name(), destinatario.strip(),
                referencia instanceof ReferenciaToken.Turno t ? t.id() : null,
                referencia instanceof ReferenciaToken.Aviso a ? a.id() : null,
                referencia instanceof ReferenciaToken.EntradaListaEspera e ? e.id() : null,
                json(new TreeMap<>(datos)), json(enlacesJson(enlaces)), ahora);
        return insertadas == 1;
    }

    /**
     * Envía las notificaciones {@code PENDIENTE} cuyo momento llegó (design D10). Cada una se procesa en su
     * propia transacción con la fila bloqueada ({@code FOR UPDATE SKIP LOCKED}): un fallo no afecta al resto ni
     * a ninguna operación de negocio, y dos ejecuciones superpuestas no envían dos veces. Nunca lanza.
     *
     * @return cantidad de notificaciones tomadas en esta ejecución
     */
    public int procesarPendientes() {
        List<Long> ids = repository.idsPendientes(consultorioActual.id(), clock.instant(), lote);
        int procesadas = 0;
        for (Long id : ids) {
            try {
                Boolean tomada = txPorNotificacion.execute(s -> procesarUna(id));
                if (Boolean.TRUE.equals(tomada)) {
                    procesadas++;
                }
            } catch (RuntimeException e) {
                log.error("Error inesperado al procesar la notificación {}: {}", id, e.getClass().getSimpleName());
            }
        }
        return procesadas;
    }

    private boolean procesarUna(Long id) {
        Instant ahora = clock.instant();
        Notificacion notificacion = repository.tomarParaProcesar(id, ahora).orElse(null);
        if (notificacion == null) {
            return false;
        }
        List<EnlaceProgramado> enlaces = notificacion.getEnlaces();
        if (enlaces.stream().anyMatch(e -> !e.venceEn().isAfter(ahora))) {
            notificacion.descartar(ENLACE_VENCIDO);
            log.info("Notificación {} ({}) descartada: enlace vencido", id, notificacion.getTipo());
            return true;
        }

        ResultadoEnvio resultado;
        List<EnlaceRenderizado> renderizados = List.of();
        try {
            renderizados = emisorEnlaces.emitir(notificacion.referencia(), enlaces);
            MensajeRenderizado mensaje = PlantillaNotificacion.de(notificacion.getTipo()).renderizar(
                    notificacion.getDatos(), renderizados, appProperties.baseUrl(), appProperties.timezone());
            resultado = canal.enviar(new NotificacionSaliente(notificacion.getTipo(), notificacion.getDestinatario(),
                    mensaje.asunto(), mensaje.cuerpo()));
        } catch (RuntimeException e) {
            // Sin el mensaje de la excepción: podría arrastrar el cuerpo o una URL con token.
            resultado = ResultadoEnvio.fallido("Error al preparar el envío: " + e.getClass().getSimpleName());
        }

        switch (resultado) {
            case ResultadoEnvio.Enviado enviado -> notificacion.marcarEnviada(ahora);
            case ResultadoEnvio.Fallido(String motivo) -> {
                // Los tokens de este intento nunca llegaron al paciente: se invalidan (solo ellos), en esta misma
                // transacción. El próximo intento emite tokens nuevos.
                emisorEnlaces.revocar(renderizados);
                switch (politica.trasFallo(notificacion.getIntentos() + 1, ahora)) {
                    case PoliticaReintento.Decision.Reprogramar(Instant programadaPara) ->
                            notificacion.registrarFalloConReintento(motivo, programadaPara);
                    case PoliticaReintento.Decision.Fallida fallida -> notificacion.registrarFalloDefinitivo(motivo);
                }
                log.info("Notificación {} ({}) falló el intento {}", id, notificacion.getTipo(),
                        notificacion.getIntentos());
            }
        }
        return true;
    }

    /**
     * Registro de envíos (design D13), de la más reciente a la más antigua.
     *
     * @param estado filtro opcional ({@code null} = todos)
     * @param tipo filtro opcional ({@code null} = todos)
     */
    @Transactional(readOnly = true)
    public Page<NotificacionVista> listar(EstadoNotificacion estado, TipoNotificacion tipo, int pagina, int tamanio) {
        PageRequest pageable = PageRequest.of(pagina, tamanio,
                Sort.by(Sort.Order.desc("creadoEn"), Sort.Order.desc("id")));
        return repository.buscar(consultorioActual.id(), estado, tipo, pageable).map(NotificacionVista::de);
    }

    /**
     * Reintento manual acotado de una notificación {@code FALLIDA} (design D13): vuelve a {@code PENDIENTE} y la
     * envía el siguiente procesamiento.
     *
     * @throws RecursoNoEncontradoException si no existe en el consultorio (404)
     * @throws ConflictoException si no está {@code FALLIDA} (409)
     * @throws ReglaNegocioException si ya tuvo {@value #MAX_REINTENTOS_MANUALES} reintentos manuales o tiene un
     *                               enlace vencido (422)
     */
    @Transactional
    public NotificacionVista reintentar(Long id) {
        Notificacion notificacion = repository.findByIdAndConsultorioId(id, consultorioActual.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("La notificación no existe."));
        if (notificacion.getEstado() != EstadoNotificacion.FALLIDA) {
            throw new ConflictoException("Solo se puede reintentar una notificación fallida.");
        }
        if (notificacion.getReintentosManuales() >= MAX_REINTENTOS_MANUALES) {
            throw new ReglaNegocioException(
                    "La notificación ya se reintentó " + MAX_REINTENTOS_MANUALES + " veces manualmente.");
        }
        Instant ahora = clock.instant();
        if (notificacion.getEnlaces().stream().anyMatch(e -> !e.venceEn().isAfter(ahora))) {
            throw new ReglaNegocioException("Un enlace de la notificación ya venció: no se puede reenviar.");
        }
        notificacion.reintentarManualmente(ahora);
        repository.flush();
        return NotificacionVista.de(notificacion);
    }

    /** Formato estable del JSON de enlaces, independiente de la configuración de Jackson: ISO-8601 en UTC. */
    private static List<Map<String, String>> enlacesJson(List<EnlaceProgramado> enlaces) {
        return enlaces.stream().map(e -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("proposito", e.proposito().name());
            m.put("venceEn", e.venceEn().toString());
            return m;
        }).toList();
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la notificación", e);
        }
    }
}
