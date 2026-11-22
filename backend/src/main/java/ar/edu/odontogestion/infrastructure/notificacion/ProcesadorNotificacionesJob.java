package ar.edu.odontogestion.infrastructure.notificacion;

import ar.edu.odontogestion.application.notificacion.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Procesamiento periódico del outbox (design D12). Retraso fijo: la siguiente ejecución empieza recién al terminar
 * la anterior. Tras un reinicio retoma los pendientes, porque viven en la base.
 */
@Component
public class ProcesadorNotificacionesJob {

    private final NotificationService notificationService;

    public ProcesadorNotificacionesJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval:PT5M}")
    public void ejecutar() {
        notificationService.procesarPendientes();
    }
}
