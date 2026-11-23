package ar.edu.odontogestion.infrastructure.scheduling;

import ar.edu.odontogestion.application.listaespera.AvisoHuecoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que pasa a {@code EXPIRADO} los avisos de hueco vencidos (C-10 D6). Se apaga con
 * {@code app.scheduling.enabled=false} (C-03 {@code SchedulingConfig}; no se redeclara {@code @EnableScheduling}).
 * Nunca propaga una excepción: el siguiente ciclo reintenta, y el log solo lleva la clase del error.
 */
@Component
public class AvisosVencidosScheduler {

    private static final Logger log = LoggerFactory.getLogger(AvisosVencidosScheduler.class);

    private final AvisoHuecoService avisos;

    public AvisosVencidosScheduler(AvisoHuecoService avisos) {
        this.avisos = avisos;
    }

    @Scheduled(fixedDelayString = "${app.lista-espera.expiracion-intervalo:PT1M}")
    public void ejecutar() {
        try {
            int expirados = avisos.expirarVencidos();
            if (expirados > 0) {
                log.info("Avisos de lista de espera expirados: {}", expirados);
            }
        } catch (RuntimeException e) {
            log.error("Falló la expiración de avisos de lista de espera: {}", e.getClass().getSimpleName());
        }
    }
}
