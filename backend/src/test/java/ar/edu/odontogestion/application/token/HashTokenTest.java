package ar.edu.odontogestion.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.TokenInvalidoException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HashTokenTest {

    private static final String VALOR_A = "A".repeat(43);
    private static final String VALOR_B = "B".repeat(43);

    @Test
    void sha256DeUnVectorConocidoCoincideConElEsperado() {
        // sha256("AAAA...A" x43) calculado con herramienta externa (sha256sum)
        byte[] hash = HashToken.sha256(VALOR_A);

        assertThat(hash).hasSize(32);
        assertThat(HexFormat.of().formatHex(hash))
                .isEqualTo("0f007385b6f9d4b7eeb2748605afe1a984a0a3bfa3f014d09e2a784ce9e5cd1a");
    }

    @Test
    void valoresDistintosProducenHashesDistintos() {
        assertThat(HashToken.sha256(VALOR_A)).isNotEqualTo(HashToken.sha256(VALOR_B));
        assertThat(HashToken.sha256(VALOR_A)).isEqualTo(HashToken.sha256(VALOR_A));
    }

    @Test
    void formatoInvalidoEsRechazadoComoTokenInvalido() {
        for (String malo : new String[] {"", "A".repeat(42), "A".repeat(44), "A".repeat(42) + "+",
                "A".repeat(42) + "/", "A".repeat(42) + "=", " " + "A".repeat(42), null}) {
            assertThatThrownBy(() -> HashToken.sha256(malo)).as(String.valueOf(malo))
                    .isInstanceOf(TokenInvalidoException.class);
        }
    }
}
