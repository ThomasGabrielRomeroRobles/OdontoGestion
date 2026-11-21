package ar.edu.odontogestion.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reloj compartido (única variante, para no multiplicar contextos de Spring). Arranca fijo en
 * {@link #AHORA}; los tests que lo avanzan deben restaurarlo con {@link MutableClock#set(Instant)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfig {

    public static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Bean
    @Primary
    public MutableClock relojFijo() {
        return new MutableClock(AHORA);
    }
}
