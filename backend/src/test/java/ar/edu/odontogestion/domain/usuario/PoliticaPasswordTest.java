package ar.edu.odontogestion.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaPasswordTest {

    @Test
    void sieteCaracteresSeRechazan() {
        assertThat(PoliticaPassword.violacion("Ab-1234")).isPresent();
        assertThatThrownBy(() -> PoliticaPassword.exigir("Ab-1234")).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void ochoCaracteresSeAceptan() {
        assertThat(PoliticaPassword.violacion("Ab-12345")).isEmpty();
        assertThatCode(() -> PoliticaPassword.exigir("Ab-12345")).doesNotThrowAnyException();
    }

    @Test
    void setentaYDosBytesSeAceptanYSetentaYTresSeRechazan() {
        assertThat(PoliticaPassword.violacion("a".repeat(72))).isEmpty();
        assertThat(PoliticaPassword.violacion("a".repeat(73))).isPresent();
    }

    @Test
    void loQueCuentaSonLosBytesUtf8NoLosCaracteres() {
        assertThat(PoliticaPassword.violacion("ñ".repeat(36))).isEmpty(); // 72 bytes
        assertThat(PoliticaPassword.violacion("ñ".repeat(37))).isPresent(); // 74 bytes
        assertThatThrownBy(() -> PoliticaPassword.exigir("ñ".repeat(37))).isInstanceOf(ReglaNegocioException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        "})
    void nulaVaciaOEnBlancoSeRechaza(String password) {
        assertThat(PoliticaPassword.violacion(password)).isPresent();
        assertThatThrownBy(() -> PoliticaPassword.exigir(password)).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void elMensajeNuncaIncluyeLaContrasena() {
        assertThat(PoliticaPassword.violacion("Corta1")).hasValueSatisfying(m -> assertThat(m).doesNotContain("Corta1"));
        assertThatThrownBy(() -> PoliticaPassword.exigir("Corta1"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Corta1"));
    }
}
