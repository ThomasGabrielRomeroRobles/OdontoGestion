package ar.edu.odontogestion.domain.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailsTest {

    @Test
    void recortaYPasaAMinusculas() {
        assertThat(Emails.normalizar("  Odontologo@Consultorio.EXAMPLE ")).isEqualTo("odontologo@consultorio.example");
    }

    @Test
    void unEmailYaNormalizadoQuedaIgual() {
        assertThat(Emails.normalizar("ana@ejemplo.test")).isEqualTo("ana@ejemplo.test");
    }

    @Test
    void nuloLanzaExcepcion() {
        assertThatThrownBy(() -> Emails.normalizar(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
