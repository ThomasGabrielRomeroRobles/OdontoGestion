package ar.edu.odontogestion.infrastructure.scheduling;

import ar.edu.odontogestion.application.recordatorio.RecordatorioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job periódico que encola los recordatorios de turno (C-11 D6). Se apaga con {@code app.scheduling.enabled=false}
 * (C-03 {@code SchedulingConfig}). Nunca propaga una excepción: el siguiente ciclo reintenta, y el log solo lleva
 * la cantidad encolada o la clase del error (sin tokens, emails ni datos del paciente).
 */
@Component
public class RecordatoriosScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatoriosScheduler.class);

    private final RecordatorioService recordatorios;

    public RecordatoriosScheduler(RecordatorioService recordatorios) {
        this.recordatorios = recordatorios;
    }

    @Scheduled(fixedDelayString = "${app.recordatorios.intervalo:PT5M}")
    public void ejecutar() {
        try {
            int encolados = recordatorios.encolarRecordatorios();
            if (encolados > 0) {
                log.info("Recordatorios de turno encolados: {}", encolados);
            }
        } catch (RuntimeException e) {
            log.error("Falló el encolado de recordatorios de turno: {}", e.getClass().getSimpleName());
        }
    }
}
