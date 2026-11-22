package ar.edu.odontogestion.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.token.PropositoToken;
import ar.edu.odontogestion.domain.token.ReferenciaToken;
import ar.edu.odontogestion.domain.token.TokenEmitido;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TokenInvalidoExceptionTest {

    @Test
    void tieneCodigoYMensajeFijos() {
        TokenInvalidoException ex = new TokenInvalidoException();

        assertThat(ex.codigo()).isEqualTo("token-invalido");
        assertThat(ex.getMessage()).isEqualTo("El enlace no es válido o ya fue utilizado.");
        assertThat(new TokenInvalidoException().getMessage()).isEqualTo(ex.getMessage());
    }

    @Test
    void noExponeConstructorQueRecibaUnValor() {
        assertThat(TokenInvalidoException.class.getConstructors()).hasSize(1);
        assertThat(Arrays.stream(TokenInvalidoException.class.getConstructors()).findFirst().orElseThrow()
                .getParameterCount()).isZero();
    }

    @Test
    void tokenEmitidoOcultaElValorEnToString() {
        String valor = "Zq8_-" + "x".repeat(38);
        TokenEmitido emitido = new TokenEmitido(valor, PropositoToken.CONFIRMAR_TURNO,
                new ReferenciaToken.Turno(900101), Instant.parse("2026-11-21T12:00:00Z"));

        assertThat(emitido.toString()).doesNotContain(valor).doesNotContain("Zq8_-");
        assertThat(emitido.toString()).contains("CONFIRMAR_TURNO");
        assertThat(emitido.valor()).isEqualTo(valor);
    }
}
