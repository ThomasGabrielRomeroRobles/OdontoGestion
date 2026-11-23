package ar.edu.odontogestion.domain.listaespera;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CompatibilidadHuecoTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate D10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate D15 = LocalDate.of(2026, 11, 15);

    private static Instant ba(String fechaHora) {
        return java.time.LocalDateTime.parse(fechaHora).atZone(BA).toInstant();
    }

    @Test
    void laFechaLocalSeCalculaEnBuenosAiresNoEnUtc() {
        // 22:30 BA del día 15 es 01:30 UTC del día 16.
        assertThat(CompatibilidadHueco.fechaLocal(ba("2026-11-15T22:30:00"), BA)).isEqualTo(D15);
        assertThat(CompatibilidadHueco.fechaLocal(Instant.parse("2026-11-16T01:30:00Z"), BA)).isEqualTo(D15);
    }

    @Test
    void compatibleSoloConLaMismaPrestacionYFechaDentroDelRangoConBordesIncluidos() {
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 1, ba("2026-11-10T09:00:00"), BA)).isTrue();
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 1, ba("2026-11-15T22:30:00"), BA)).isTrue();
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 1, ba("2026-11-12T10:00:00"), BA)).isTrue();
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 1, ba("2026-11-16T09:00:00"), BA)).isFalse();
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 1, ba("2026-11-09T18:00:00"), BA)).isFalse();
        assertThat(CompatibilidadHueco.compatible(1, D10, D15, 2, ba("2026-11-12T10:00:00"), BA)).isFalse();
    }

    @Test
    void ofrecibleExigeLaAnticipacionMinimaDeReserva() {
        Instant ahora = Instant.parse("2026-11-20T12:00:00Z");
        assertThat(CompatibilidadHueco.ofrecible(ahora.plusSeconds(121 * 60), ahora, 120)).isTrue();
        assertThat(CompatibilidadHueco.ofrecible(ahora.plusSeconds(120 * 60), ahora, 120)).isFalse();
        assertThat(CompatibilidadHueco.ofrecible(ahora.plusSeconds(60 * 60), ahora, 120)).isFalse();
        assertThat(CompatibilidadHueco.ofrecible(ahora.minusSeconds(60), ahora, 0)).isFalse();
    }

    @Test
    void venceEnEsElMenorEntreElParametroYElInicioDelHueco() {
        Instant ahora = Instant.parse("2026-11-20T12:00:00Z");
        assertThat(CompatibilidadHueco.venceEn(ahora, 60, ahora.plusSeconds(3 * 3600)))
                .isEqualTo(ahora.plusSeconds(3600));
        assertThat(CompatibilidadHueco.venceEn(ahora, 60, ahora.plusSeconds(30 * 60)))
                .isEqualTo(ahora.plusSeconds(30 * 60));
        assertThat(CompatibilidadHueco.venceEn(ahora, 60, ahora.plusSeconds(3600)))
                .isEqualTo(ahora.plusSeconds(3600));
    }
}
