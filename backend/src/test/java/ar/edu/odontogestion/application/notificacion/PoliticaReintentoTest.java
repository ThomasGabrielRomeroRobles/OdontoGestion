package ar.edu.odontogestion.application.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reintentos con espera creciente y tope de intentos (RN-NO-06, design D10). */
class PoliticaReintentoTest {

    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");
    private final PoliticaReintento politica =
            new PoliticaReintento(3, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)));

    @Test
    void falloEnElPrimerIntentoReprogramaAUnMinuto() {
        assertThat(politica.trasFallo(1, AHORA))
                .isEqualTo(new PoliticaReintento.Decision.Reprogramar(AHORA.plus(Duration.ofMinutes(1))));
    }

    @Test
    void falloEnElSegundoIntentoReprogramaACincoMinutosMasQueLaAnterior() {
        var primera = (PoliticaReintento.Decision.Reprogramar) politica.trasFallo(1, AHORA);
        var segunda = (PoliticaReintento.Decision.Reprogramar) politica.trasFallo(2, AHORA);

        assertThat(segunda.programadaPara()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(Duration.between(AHORA, segunda.programadaPara()))
                .isGreaterThan(Duration.between(AHORA, primera.programadaPara()));
    }

    @Test
    void falloEnElTercerIntentoDejaLaNotificacionFallida() {
        assertThat(politica.trasFallo(3, AHORA)).isEqualTo(new PoliticaReintento.Decision.Fallida());
        assertThat(politica.trasFallo(4, AHORA)).isEqualTo(new PoliticaReintento.Decision.Fallida());
    }

    @Test
    void conMenosEsperasQueIntentosSeRepiteLaUltima() {
        PoliticaReintento cinco = new PoliticaReintento(5, List.of(Duration.ofMinutes(1), Duration.ofMinutes(5)));

        assertThat(cinco.trasFallo(4, AHORA))
                .isEqualTo(new PoliticaReintento.Decision.Reprogramar(AHORA.plus(Duration.ofMinutes(5))));
    }

    @Test
    void unSoloIntentoFallaDeEntrada() {
        assertThat(new PoliticaReintento(1, List.of(Duration.ofMinutes(1))).trasFallo(1, AHORA))
                .isEqualTo(new PoliticaReintento.Decision.Fallida());
    }

    @Test
    void configuracionInvalidaEsRechazada() {
        assertThatThrownBy(() -> new PoliticaReintento(0, List.of(Duration.ofMinutes(1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaReintento(3, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.trasFallo(0, AHORA)).isInstanceOf(IllegalArgumentException.class);
    }
}
