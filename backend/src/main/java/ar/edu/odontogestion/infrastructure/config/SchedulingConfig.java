package ar.edu.odontogestion.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs {@code @Scheduled} en el mismo proceso, sin colas (DD-13). Se apaga con {@code app.scheduling.enabled=false}
 * (los tests de integración lo hacen para invocar los jobs a mano).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
