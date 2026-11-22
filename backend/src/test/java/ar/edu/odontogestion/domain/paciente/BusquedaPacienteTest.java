package ar.edu.odontogestion.domain.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusquedaPacienteTest {

    @Test
    void elPatronEsContieneEnMinusculasYRecortado() {
        assertThat(BusquedaPaciente.de("  PÉrez ").patron()).isEqualTo("%pérez%");
    }

    @Test
    void elPatronEscapaComodinesYLaBarra() {
        assertThat(BusquedaPaciente.de("50%_a\\b").patron()).isEqualTo("%50\\%\\_a\\\\b%");
    }

    @Test
    void elPrefijoDeDniUsaSoloLosDigitos() {
        assertThat(BusquedaPaciente.de("30.12").prefijoDni()).isEqualTo("3012%");
    }

    @Test
    void sinDigitosElPrefijoNoCoincideConNingunDni() {
        // '' sin comodín solo coincide con un DNI vacío, que la base no admite (CHECK de V8).
        assertThat(BusquedaPaciente.de("pérez").prefijoDni()).isEmpty();
    }
}
