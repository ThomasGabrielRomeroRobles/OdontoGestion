package ar.edu.odontogestion.application.recordatorio;

import ar.edu.odontogestion.application.consultorio.ConsultorioActual;
import ar.edu.odontogestion.application.consultorio.ConsultorioService;
import ar.edu.odontogestion.application.notificacion.NotificationService;
import ar.edu.odontogestion.domain.notificacion.EnlaceProgramado;
import ar.edu.odontogestion.domain.notificacion.TipoNotificacion;
import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.turno.Turno;
import ar.edu.odontogestion.domain.turno.TurnoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Selecciona los turnos a recordar y encola su {@code RECORDATORIO} (C-11, design D1–D5). Sin estado: cada corrida
 * toma lo que esté en la ventana {@code (ahora, ahora + H]}; la idempotencia y la concurrencia las garantiza el
 * índice único parcial {@code uq_notificacion_turno_tipo} vía {@link NotificationService#encolar} ({@code ON
 * CONFLICT DO NOTHING}). Cada turno se encola en su propia transacción: un fallo no afecta a los demás.
 *
 * <p>Los logs llevan solo el id del turno y la clase del error: nunca emails, tokens ni datos del paciente.
 */
@Service
public class RecordatorioService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioService.class);

    private final TurnoRepository turnos;
    private final NotificationService notificaciones;
    private final ConsultorioService consultorioService;
    private final ConsultorioActual consultorioActual;
    private final Clock clock;
    private final TransactionTemplate txLectura;
    private final TransactionTemplate txPorTurno;

    public RecordatorioService(TurnoRepository turnos, NotificationService notificaciones,
            ConsultorioService consultorioService, ConsultorioActual consultorioActual, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.turnos = turnos;
        this.notificaciones = notificaciones;
        this.consultorioService = consultorioService;
        this.consultorioActual = consultorioActual;
        this.clock = clock;
        this.txLectura = new TransactionTemplate(transactionManager);
        this.txLectura.setReadOnly(true);
        this.txPorTurno = new TransactionTemplate(transactionManager);
        this.txPorTurno.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Encola un recordatorio por cada turno {@code RESERVADO} en ventana que todavía no tenga uno.
     *
     * @return cantidad de recordatorios efectivamente encolados en esta corrida
     */
    public int encolarRecordatorios() {
        int horas = consultorioService.parametrosVigentes().recordatorioHorasAntes();
        Instant ahora = clock.instant();
        Instant hasta = ahora.plus(Duration.ofHours(horas));
        Long consultorioId = consultorioActual.id();
        List<Long> ids = txLectura.execute(s -> turnos.idsParaRecordatorio(consultorioId, ahora, hasta, horas));
        int encolados = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                if (Boolean.TRUE.equals(txPorTurno.execute(s -> encolar(id, consultorioId)))) {
                    encolados++;
                }
            } catch (RuntimeException e) {
                log.error("No se pudo encolar el recordatorio del turno {}: {}", id, e.getClass().getSimpleName());
            }
        }
        return encolados;
    }

    private boolean encolar(Long turnoId, Long consultorioId) {
        Turno turno = turnos.findByIdAndConsultorio_Id(turnoId, consultorioId).orElse(null);
        if (turno == null) {
            return false;
        }
        String email = turno.getPaciente().getEmail();
        if (email == null || email.isBlank()) {
            log.warn("No se encola RECORDATORIO del turno {}: el paciente no tiene email", turnoId);
            return false;
        }
        Map<String, String> datos = Map.of(
                "consultorio", turno.getConsultorio().getNombre(),
                "inicio", turno.getInicio().toString(),
                "prestacion", turno.getPrestacion().getNombre());
        List<EnlaceProgramado> enlaces = List.of(
                new EnlaceProgramado(PropositoToken.CONFIRMAR_TURNO, turno.getInicio()),
                new EnlaceProgramado(PropositoToken.CANCELAR_TURNO, turno.getInicio()));
        return notificaciones.encolar(TipoNotificacion.RECORDATORIO, email, new ReferenciaToken.Turno(turnoId), datos,
                enlaces);
    }
}
