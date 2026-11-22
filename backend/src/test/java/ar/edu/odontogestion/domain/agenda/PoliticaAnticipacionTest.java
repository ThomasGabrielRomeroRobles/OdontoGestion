package ar.edu.odontogestion.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PoliticaAnticipacionTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    /** Lunes 08:00 locales. */
    private static final Instant AHORA = LUNES.atTime(8, 0).atZone(BA).toInstant();

    private static List<Hueco> huecos(PoliticaAnticipacion politica) {
        // Horario 08:30-10:30 con huecos de 30 min cada 30: 08:30 (a 30 min), 09:00 (60), 09:30 (90), 10:00 (120).
        return CalculadoraDisponibilidad.calcular(LUNES, LUNES, BA,
                List.of(new FranjaHoraria(1, LocalTime.of(8, 30), LocalTime.of(10, 30))),
                List.of(), List.of(), 30, 30, politica.minimoInicio(AHORA));
    }

    private static Instant local(int h, int m) {
        return LUNES.atTime(h, m).atZone(BA).toInstant();
    }

    @Test
    void odontologoNoTieneAnticipacionYExcluyeSoloLoPasado() {
        assertThat(PoliticaAnticipacion.odontologo().minimoInicio(AHORA)).isEqualTo(AHORA);

        assertThat(huecos(PoliticaAnticipacion.odontologo())).extracting(Hueco::inicio)
                .containsExactly(local(8, 30), local(9, 0), local(9, 30), local(10, 0));
    }

    @Test
    void odontologoExcluyeUnHuecoQueEmpezoAntesDeAhora() {
        Instant tarde = LUNES.atTime(9, 10).atZone(BA).toInstant();

        assertThat(PoliticaAnticipacion.odontologo().minimoInicio(tarde)).isEqualTo(tarde);
        assertThat(CalculadoraDisponibilidad.calcular(LUNES, LUNES, BA,
                List.of(new FranjaHoraria(1, LocalTime.of(8, 30), LocalTime.of(10, 30))), List.of(), List.of(),
                30, 30, PoliticaAnticipacion.odontologo().minimoInicio(tarde))).extracting(Hueco::inicio)
                .containsExactly(local(9, 30), local(10, 0));
    }

    @Test
    void publicaExcluyeUnHuecoA60MinutosEIncluyeUnoA120Exactos() {
        PoliticaAnticipacion publica = PoliticaAnticipacion.publica(120);

        assertThat(publica.minimoInicio(AHORA)).isEqualTo(AHORA.plusSeconds(120 * 60));
        assertThat(huecos(publica)).extracting(Hueco::inicio).containsExactly(local(10, 0))
                .doesNotContain(local(9, 0));
    }

    @Test
    void publicaConOtraAnticipacionMueveElLimite() {
        assertThat(huecos(PoliticaAnticipacion.publica(60))).extracting(Hueco::inicio)
                .containsExactly(local(9, 0), local(9, 30), local(10, 0));
        assertThat(PoliticaAnticipacion.publica(0).minimoInicio(AHORA)).isEqualTo(AHORA);
    }
}
