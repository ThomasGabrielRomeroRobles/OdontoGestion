package ar.edu.odontogestion.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void avanzaSesentaYUnSegundos() {
        MutableClock reloj = new MutableClock(INICIO);

        reloj.advance(Duration.ofSeconds(61));

        assertThat(reloj.instant()).isEqualTo(Instant.parse("2026-11-20T12:01:01Z"));
    }

    @Test
    void sinAvanzarElInstanteEsElInicial() {
        assertThat(new MutableClock(INICIO).instant()).isEqualTo(INICIO);
    }

    @Test
    void losAvancesSeAcumulanYLaZonaEsUtc() {
        MutableClock reloj = new MutableClock(INICIO);

        reloj.advance(Duration.ofMinutes(1));
        reloj.advance(Duration.ofMinutes(2));

        assertThat(reloj.instant()).isEqualTo(INICIO.plus(Duration.ofMinutes(3)));
        assertThat(reloj.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
