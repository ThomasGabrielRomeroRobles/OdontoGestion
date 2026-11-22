package ar.edu.odontogestion.application.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneradorTokensTest {

    @Test
    void generaValorDe43CaracteresUrlSafeQueDecodificaA32Bytes() {
        GeneradorTokens generador = new GeneradorTokens(new SecureRandom());

        String valor = generador.generar();

        assertThat(valor).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(Base64.getUrlDecoder().decode(valor)).hasSize(32);
    }

    @Test
    void milValoresSonDistintos() {
        GeneradorTokens generador = new GeneradorTokens(new SecureRandom());
        Set<String> valores = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            valores.add(generador.generar());
        }
        assertThat(valores).hasSize(1000);
    }

    @Test
    void usaElGeneradorAleatorioInyectado() {
        SecureRandom fijo = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 0xFF);
            }
        };
        String valor = new GeneradorTokens(fijo).generar();
        assertThat(Base64.getUrlDecoder().decode(valor)).containsOnly((byte) 0xFF);
        assertThat(valor).isEqualTo("_".repeat(42) + "8");
    }
}
