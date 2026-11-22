package ar.edu.odontogestion.application.notificacion;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import ar.edu.odontogestion.domain.notificacion.CanalNotificacion;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.NotificacionRepository;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox de notificaciones (C-03, design D6–D8, D10).
 *
 * <p>Contrato para C-07/C-09/C-10/C-11: llamar a {@link #encolar} <b>dentro de la transacción de la operación de
 * negocio</b>; la notificación se confirma o se descarta junto con ella y nunca se envía en ese momento. El envío
 * lo hace el procesamiento periódico.
 */
@Service
public class NotificationService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificacionRepository repository;
    private final ConsultorioActual consultorioActual;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public NotificationService(NotificacionRepository repository, ConsultorioActual consultorioActual,
            ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.consultorioActual = consultorioActual;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
