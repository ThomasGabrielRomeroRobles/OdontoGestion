package ar.edu.odontogestion.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj fijo compartido (única variante, para no multiplicar contextos de Spring). */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfig {

    public static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Bean
    @Primary
    public Clock relojFijo() {
        return Clock.fixed(AHORA, ZoneOffset.UTC);
    }
}
