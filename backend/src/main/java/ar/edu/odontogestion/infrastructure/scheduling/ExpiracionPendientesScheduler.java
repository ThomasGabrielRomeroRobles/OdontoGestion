package ar.edu.odontogestion.infrastructure.scheduling;

import ar.edu.odontogestion.application.turno.ExpiracionTurnosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que expira las solicitudes pendientes vencidas (design D8). Se apaga con
 * {@code app.scheduling.enabled=false} (C-03 {@code SchedulingConfig}; no se redeclara {@code @EnableScheduling}).
 * Nunca propaga una excepción: el siguiente ciclo reintenta, y el log solo lleva la clase del error.
 */
@Component
public class ExpiracionPendientesScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiracionPendientesScheduler.class);

    private final ExpiracionTurnosService expiracion;

    public ExpiracionPendientesScheduler(ExpiracionTurnosService expiracion) {
        this.expiracion = expiracion;
    }

    @Scheduled(fixedDelayString = "${app.turnos.expiracion-intervalo:PT1M}")
    public void ejecutar() {
        try {
            int expirados = expiracion.expirarVencidas();
            if (expirados > 0) {
                log.info("Solicitudes pendientes expiradas: {}", expirados);
            }
        } catch (RuntimeException e) {
            log.error("Falló la expiración de solicitudes pendientes: {}", e.getClass().getSimpleName());
        }
    }
}
