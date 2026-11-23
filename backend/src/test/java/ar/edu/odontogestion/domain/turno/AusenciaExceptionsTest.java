package ar.edu.odontogestion.domain.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.odontogestion.domain.error.ReglaNegocioException;
import org.junit.jupiter.api.Test;

/** Excepciones del ausentismo (C-08 design D2): 422 con código estable y mensaje fijo, sin datos del paciente. */
class AusenciaExceptionsTest {

    @Test
    void antesDeLaToleranciaEsReglaDeNegocioConCodigoEstable() {
        AusenteAntesDeToleranciaException ex = new AusenteAntesDeToleranciaException();

        assertThat(ex).isInstanceOf(ReglaNegocioException.class);
        assertThat(ex.codigo()).isEqualTo("ausente-antes-de-tolerancia");
        assertThat(ex.getMessage()).isEqualTo(new AusenteAntesDeToleranciaException().getMessage()).isNotBlank();
    }

    @Test
    void reversionFueraDePlazoEsReglaDeNegocioConCodigoEstable() {
        ReversionFueraDePlazoException ex = new ReversionFueraDePlazoException();

        assertThat(ex).isInstanceOf(ReglaNegocioException.class);
        assertThat(ex.codigo()).isEqualTo("reversion-fuera-de-plazo");
        assertThat(ex.getMessage()).isEqualTo(new ReversionFueraDePlazoException().getMessage()).contains("24");
    }
}
