package ar.edu.odontogestion.domain.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.ErrorCampo;
import ar.edu.odontogestion.domain.error.ValidacionDominioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DniTest {

    @ParameterizedTest
    @CsvSource({
            "30.123.456, 30123456",
            "' 7 123 456 ', 7123456",
            "30-123-456, 30123456",
            "30123456, 30123456",
            "7123456, 7123456"
    })
    void quitaPuntosEspaciosYGuiones(String entrada, String esperado) {
        assertThat(Dni.normalizar(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123456", "123456789", "12AB", "30.123.45a"})
    void rechazaLoQueNoSonSieteUOchoDigitos(String entrada) {
        assertThatThrownBy(() -> Dni.normalizar(entrada))
                .isInstanceOf(ValidacionDominioException.class)
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .extracting(ErrorCampo::field).containsExactly("dni"));
    }

    @Test
    void elMensajeDeErrorNoIncluyeElValorRecibido() {
        assertThatThrownBy(() -> Dni.normalizar("123456"))
                .satisfies(e -> assertThat(((ValidacionDominioException) e).errores())
                        .allSatisfy(err -> assertThat(err.message()).doesNotContain("123456")));
    }
}
