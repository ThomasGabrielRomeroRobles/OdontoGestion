package ar.edu.odontogestion.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    void elRelojPorDefectoOperaEnUtc() {
        Clock clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void elRelojPorDefectoEsElTiempoRealDelSistema() {
        Clock clock = new ClockConfig().clock();

        assertThat(Duration.between(Instant.now(), clock.instant()).abs()).isLessThan(Duration.ofSeconds(5));
    }
}
